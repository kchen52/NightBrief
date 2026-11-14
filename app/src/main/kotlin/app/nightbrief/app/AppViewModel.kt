package app.nightbrief.app

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.nightbrief.astro.NightEphemeris
import app.nightbrief.astro.SkyEvent
import app.nightbrief.astro.SkyEvents
import app.nightbrief.astro.Target
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.data.EmptyLibraryException
import app.nightbrief.data.LibraryFormatException
import app.nightbrief.data.LibraryTransfer
import app.nightbrief.gear.GearKit
import app.nightbrief.score.AuroraAlerts
import app.nightbrief.score.BigNightAlerts
import app.nightbrief.score.DarkSkyCandidate
import app.nightbrief.score.DarkSkyFinder
import app.nightbrief.score.SessionCapture
import app.nightbrief.score.SessionEntry
import app.nightbrief.score.SessionPrompt
import app.nightbrief.score.UnitSystem
import app.nightbrief.score.Briefing
import app.nightbrief.score.NightReport
import app.nightbrief.score.WindowAlarms
import app.nightbrief.sites.Site
import app.nightbrief.sites.SiteBook
import app.nightbrief.weather.PlaceSearchResult
import app.nightbrief.app.update.ApkInstaller
import app.nightbrief.app.update.AppUpdateUiState
import app.nightbrief.app.update.AvailableUpdate
import app.nightbrief.app.update.GithubReleaseClient
import app.nightbrief.work.DigestScheduler
import app.nightbrief.work.WidgetRefresh
import app.nightbrief.work.WindowAlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class BriefingUiState(
    val briefing: Briefing? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class DarkerSkyUiState(
    val searching: Boolean = false,
    val candidates: List<DarkSkyCandidate>? = null,
    val error: String? = null,
    val searchedSiteId: String? = null,
    val savedIds: Set<String> = emptySet(),
    val accessFilter: DarkerSkyAccessFilter = DarkerSkyAccessFilter.ALL,
)

/** Tri-state access filter for darker-sky candidates. UNKNOWN access shows in ALL only. */
enum class DarkerSkyAccessFilter { ALL, DRIVE_UP, HIKE_IN }

class AppViewModel(
    app: Application,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : AndroidViewModel(app) {
    /**
     * "Now" for the on-device sky reads. It is a property, not a constructor parameter, because
     * the framework instantiates this ViewModel reflectively and only knows the
     * `(Application)` and `(Application, SavedStateHandle)` signatures; another parameter makes
     * `by viewModels()` throw on device. Tests may pin it before composing.
     */
    internal var clock: Clock = Clock.systemUTC()

    private val graph = AppGraph.get(app)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _libraryNote = MutableStateFlow<String?>(null)
    /** One-shot import outcome. Call [consumeLibraryNote] after the snackbar is shown. */
    val libraryNote: StateFlow<String?> = _libraryNote.asStateFlow()

    private val _libraryBusy = MutableStateFlow(false)
    val libraryBusy: StateFlow<Boolean> = _libraryBusy.asStateFlow()

    /** Clears [libraryNote] so a later identical outcome can surface and rotation does not replay it. */
    fun consumeLibraryNote() {
        _libraryNote.value = null
    }

    val state: StateFlow<AppState?> = graph.settings.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _briefing = MutableStateFlow(BriefingUiState())
    val briefing: StateFlow<BriefingUiState> = _briefing.asStateFlow()

    private val _selectedSiteId = MutableStateFlow<String?>(null)
    val selectedSiteId: StateFlow<String?> = _selectedSiteId.asStateFlow()

    private val _darkerSky = MutableStateFlow(DarkerSkyUiState())
    val darkerSky: StateFlow<DarkerSkyUiState> = _darkerSky.asStateFlow()

    private var darkerSkyJob: Job? = null

    private val updateClient = GithubReleaseClient(
        owner = BuildConfig.GITHUB_OWNER,
        repo = BuildConfig.GITHUB_REPO,
    )
    private val _appUpdate = MutableStateFlow(AppUpdateUiState())
    val appUpdate: StateFlow<AppUpdateUiState> = _appUpdate.asStateFlow()
    private var updateJob: Job? = null
    private var lastUpdateCheckAt: Instant? = null

    /**
     * Onboarding drafts. Stored in [SavedStateHandle] so they survive process death,
     * not only configuration changes.
     */
    val onboardingSite = MutableStateFlow(readSite())
    val onboardingGear = MutableStateFlow(readGear())

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            state.filterNotNull()
                .map { listOf(it.onboardingComplete, it.sites, it.gear, it.customTargets) }
                .distinctUntilChanged()
                .collectLatest { if (it.first() == true) refresh(force = false) }
        }
        viewModelScope.launch {
            state.filterNotNull()
                .map { it.skippedUpdateId }
                .distinctUntilChanged()
                .collect { skipped ->
                    _appUpdate.update { current ->
                        if (skipped != null && current.available?.id == skipped) {
                            current.copy(available = null)
                        } else {
                            current
                        }
                    }
                }
        }
        checkForUpdate()
    }

    /**
     * Asks GitHub whether a newer APK exists. Failure is silent: the banner stays hidden and
     * the briefing is untouched. [force] bypasses the interval gate (resume / pull-to-check).
     */
    fun checkForUpdate(force: Boolean = false) {
        if (!force) {
            val last = lastUpdateCheckAt
            if (last != null && Instant.now().isBefore(last.plusSeconds(UPDATE_CHECK_INTERVAL_SEC))) return
        }
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            lastUpdateCheckAt = Instant.now()
            val skipped = state.value?.skippedUpdateId
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    when (BuildConfig.UPDATE_CHANNEL) {
                        "develop" -> updateClient.checkDevelop(BuildConfig.GIT_SHA)
                        else -> updateClient.checkStable(BuildConfig.VERSION_NAME)
                    }
                }.getOrNull()
            }
            if (found == null || found.id == skipped) {
                _appUpdate.update {
                    it.copy(available = null, downloadFailed = false, needsInstallPermission = false)
                }
            } else {
                _appUpdate.update {
                    it.copy(
                        available = found,
                        downloadFailed = false,
                        needsInstallPermission = false,
                        progress = null,
                    )
                }
            }
        }
    }

    fun skipUpdate(update: AvailableUpdate) {
        mutate { it.copy(skippedUpdateId = update.id) }
        _appUpdate.update { it.copy(available = null, progress = null, downloadFailed = false) }
    }

    /**
     * Downloads the APK and opens the system installer. Asks for the unknown-sources
     * permission first when Android requires it. Never throws into the UI.
     */
    fun installUpdate(update: AvailableUpdate) {
        val app = getApplication<Application>()
        if (!ApkInstaller.canInstall(app)) {
            _appUpdate.update { it.copy(needsInstallPermission = true, downloadFailed = false) }
            return
        }
        if (_appUpdate.value.progress != null) return
        viewModelScope.launch {
            _appUpdate.update { it.copy(progress = 0f, downloadFailed = false, needsInstallPermission = false) }
            val file = withContext(Dispatchers.IO) {
                updateClient.download(
                    update,
                    destDir = File(app.cacheDir, "updates"),
                    onProgress = { value -> _appUpdate.update { state -> state.copy(progress = value) } },
                )
            }
            if (file == null) {
                _appUpdate.update { it.copy(progress = null, downloadFailed = true) }
                return@launch
            }
            _appUpdate.update { it.copy(progress = null) }
            if (!ApkInstaller.install(app, file)) {
                _appUpdate.update { it.copy(downloadFailed = true) }
            }
        }
    }

    fun openInstallPermissionSettings() {
        ApkInstaller.openInstallPermissionSettings(getApplication())
        _appUpdate.update { it.copy(needsInstallPermission = false) }
    }

    fun refresh(force: Boolean) {
        val s = state.value ?: return
        if (s.sites.sites.isEmpty()) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _briefing.update { it.copy(loading = true, error = null) }
            try {
                val started = System.nanoTime()
                val result = withContext(Dispatchers.Default) {
                    graph.briefings.brief(
                        sites = s.sites.primaryFirst(),
                        kit = s.gear,
                        forceRefresh = force,
                        customTargets = s.customTargets,
                    )
                }
                val tookMs = (System.nanoTime() - started) / 1_000_000
                val statuses = result.tonight.joinToString(",") { it.forecastStatus?.name ?: "null" }
                Log.d(AppGraph.NET_LOG_TAG, "brief done sites=${s.sites.sites.size} force=$force took=${tookMs}ms statuses=[$statuses]")
                _briefing.value = BriefingUiState(result, loading = false)
                WindowAlarmScheduler.onBriefingRefreshed(getApplication(), result)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _briefing.update {
                    it.copy(loading = false, error = e.message ?: getApplication<Application>().getString(R.string.error_generic))
                }
            }
        }
    }

    fun selectSite(id: String?) {
        _selectedSiteId.value = id
    }

    suspend fun planNight(siteId: String, date: LocalDate): NightReport? {
        val s = state.value ?: return null
        val site = s.sites[siteId] ?: return null
        val started = System.nanoTime()
        val report = withContext(Dispatchers.Default) {
            graph.briefings.plan(site, date, s.gear, customTargets = s.customTargets)
        }
        val tookMs = (System.nanoTime() - started) / 1_000_000
        Log.d(AppGraph.NET_LOG_TAG, "plan done date=$date took=${tookMs}ms status=${report.forecastStatus}")
        return report
    }

    /**
     * Year-ahead sky events for [zone], computed off the main thread. Events shows a loading
     * state until this returns; it must never run during composition.
     */
    suspend fun skyEventsYearAhead(zone: ZoneId): List<SkyEvent> =
        withContext(Dispatchers.Default) {
            SkyEvents.yearAhead(LocalDate.now(clock.withZone(zone)), zone)
        }

    /**
     * Tonight's sunset, sunrise, and dark window from the on-device ephemeris, for the loading
     * state before the first forecast arrives. Off the main thread for the same reason.
     */
    suspend fun loadingEphemeris(site: Site): NightEphemeris =
        withContext(Dispatchers.Default) {
            NightEphemeris.compute(
                LocalDate.now(clock.withZone(site.zone)), site.zone, site.latitude, site.longitude,
            )
        }

    /** Bortle class from the bundled light-pollution grid, or null when those grids have no data there. */
    suspend fun lookupBortle(lat: Double, lon: Double): Int? =
        withContext(Dispatchers.IO) { graph.bortleLookup?.lookup(lat, lon) }

    /** IANA time zone from Open-Meteo, or null when the lookup fails. */
    suspend fun lookupTimeZone(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) { graph.timeZoneLookup.zoneFor(lat, lon) }

    /** Fuzzy place-name search. A failure is [PlaceSearchResult.failed], not a thrown error. */
    suspend fun searchPlaces(query: String): PlaceSearchResult =
        withContext(Dispatchers.IO) { graph.placeSearch.search(query) }

    /**
     * Samples the bundled Bortle grid in rings around [siteId] and scores the darkest
     * land spots for tonight. Open water is dropped before scoring. One bad grid cell
     * or candidate forecast skips that candidate only. A water mask that cannot be
     * read leaves the sample in.
     */
    fun searchDarkerSky(siteId: String) {
        val s = state.value ?: return
        val report = _briefing.value.briefing?.reportFor(siteId) ?: return
        darkerSkyJob?.cancel()
        val filter = _darkerSky.value.accessFilter
        darkerSkyJob = viewModelScope.launch {
            _darkerSky.value = DarkerSkyUiState(searching = true, searchedSiteId = siteId, accessFilter = filter)
            try {
                val lookup = graph.bortleLookup
                if (lookup == null) {
                    _darkerSky.value = DarkerSkyUiState(
                        error = getApplication<Application>().getString(R.string.darker_sky_no_grid),
                        searchedSiteId = siteId,
                        accessFilter = filter,
                    )
                    return@launch
                }
                // Scoring, comet parsing, and the grid samples resume on Default, not on the caller.
                val found = withContext(Dispatchers.Default) {
                    DarkSkyFinder.search(
                        report,
                        s.gear,
                        lookup,
                        graph.briefings,
                        roads = graph.roadAccess,
                        water = graph.openWater,
                    )
                }
                // Reverse geocoding is per candidate. Parallel with failure isolation: a missing
                // place name leaves the distance label and never fails the search.
                val named = coroutineScope {
                    found.map { candidate ->
                        async {
                            val place = graph.placeSearch.reverse(candidate.site.latitude, candidate.site.longitude)
                            if (place == null) candidate else candidate.copy(placeName = place.name)
                        }
                    }.awaitAll()
                }
                // A cancelled or replaced search must not publish into a newer site's card.
                if (!isActive || _darkerSky.value.searchedSiteId != siteId) return@launch
                val book = state.value?.sites ?: s.sites
                _darkerSky.value = DarkerSkyUiState(
                    candidates = named,
                    searchedSiteId = siteId,
                    accessFilter = filter,
                    savedIds = savedDarkSkyIds(book, found),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _darkerSky.value = DarkerSkyUiState(
                    error = e.message ?: getApplication<Application>().getString(R.string.error_generic),
                    searchedSiteId = siteId,
                    accessFilter = filter,
                )
            }
        }
    }

    /** Tri-state access filter. Pure UI filtering; it never refetches or rescores. */
    fun setDarkerSkyAccessFilter(filter: DarkerSkyAccessFilter) {
        _darkerSky.update { it.copy(accessFilter = filter) }
    }

    fun clearDarkerSky() {
        darkerSkyJob?.cancel()
        _darkerSky.value = DarkerSkyUiState()
    }

    /**
     * Saves a darker-sky candidate as a regular site (fresh id, not primary).
     * A site already within [SAME_DARK_SKY_KM] is left as it is.
     */
    fun saveDarkerSky(candidate: DarkSkyCandidate) {
        _darkerSky.update { it.copy(savedIds = it.savedIds + candidate.site.id) }
        val id = java.util.UUID.randomUUID().toString()
        mutate { current ->
            if (candidate.site.id in savedDarkSkyIds(current.sites, listOf(candidate))) current
            else current.copy(sites = current.sites.add(candidate.site.copy(id = id)))
        }
    }

    fun updateOnboardingSite(draft: SiteDraft) {
        onboardingSite.value = draft
        savedState[ONBOARDING_SITE] = json.encodeToString(SiteDraft.serializer(), draft)
    }

    fun updateOnboardingGear(kit: GearKit) {
        onboardingGear.value = kit
        savedState[ONBOARDING_GEAR] = json.encodeToString(GearKit.serializer(), kit)
    }

    /** Values currently held for process-death restore. Tests rebuild a handle from this map. */
    internal fun exportOnboardingState(): Map<String, Any?> = buildMap {
        savedState.get<String>(ONBOARDING_SITE)?.let { put(ONBOARDING_SITE, it) }
        savedState.get<String>(ONBOARDING_GEAR)?.let { put(ONBOARDING_GEAR, it) }
    }

    private fun readSite(): SiteDraft {
        val raw = savedState.get<String>(ONBOARDING_SITE) ?: return SiteDraft(name = SiteDraft.UNTOUCHED_NAME, makePrimary = true)
        return runCatching { json.decodeFromString(SiteDraft.serializer(), raw) }
            .getOrDefault(SiteDraft(name = SiteDraft.UNTOUCHED_NAME, makePrimary = true))
    }

    private fun readGear(): GearKit? {
        val raw = savedState.get<String>(ONBOARDING_GEAR) ?: return null
        return runCatching { json.decodeFromString(GearKit.serializer(), raw) }.getOrNull()
    }

    fun completeOnboarding(digestTime: String) {
        val draft = onboardingSite.value
        if (!draft.isValid) return
        mutate {
            it.copy(
                onboardingComplete = true,
                onboardingCompletedAtEpochSec = java.time.Instant.now().epochSecond,
                sites = it.sites.add(draft.toSite(), makePrimary = true),
                gear = onboardingGear.value ?: it.gear,
                digestTime = digestTime,
            )
        }
    }

    fun saveSite(draft: SiteDraft) {
        if (!draft.isValid) return
        val site = draft.toSite()
        mutate {
            val book = if (it.sites[site.id] != null) it.sites.update(site) else it.sites.add(site)
            it.copy(sites = if (draft.makePrimary) book.setPrimary(site.id) else book)
        }
    }

    fun deleteSite(id: String) = mutate {
        val sites = it.sites.delete(id)
        it.copy(sites = sites, windowAlarm = WindowAlarms.syncWithSites(it.windowAlarm, sites))
    }

    fun toggleWindowAlarm(report: NightReport) = mutate { WindowAlarmScheduler.toggle(it, report) }
    /**
     * Puts [previous] back after a delete, but only while the book is still exactly [expected]
     * (the book right after that delete). Any edit in between wins and the undo is dropped.
     */
    fun restoreSites(previous: SiteBook, expected: SiteBook) =
        mutate { if (it.sites == expected) it.copy(sites = previous) else it }
    fun setPrimary(id: String) = mutate { it.copy(sites = it.sites.setPrimary(id)) }
    fun moveSite(from: Int, to: Int) = mutate { it.copy(sites = it.sites.move(from, to)) }

    fun updateGear(transform: (GearKit) -> GearKit) = mutate { it.copy(gear = transform(it.gear)) }

    fun addCustomTarget(target: Target) = mutate { current ->
        val cleaned = target.copy(tracksMoon = false, requiresBrightMoon = false)
        if (cleaned.id in current.customTargets.map { it.id }) current
        else current.copy(customTargets = current.customTargets + cleaned)
    }

    fun removeCustomTarget(id: String) = mutate { current ->
        current.copy(customTargets = current.customTargets.filterNot { it.id == id })
    }

    fun setDigestTime(time: String) = mutate { it.copy(digestTime = time) }
    fun setDigestEnabled(enabled: Boolean) = mutate { it.copy(digestEnabled = enabled) }
    fun setBigNightAlertsEnabled(enabled: Boolean) = mutate { it.copy(bigNightAlertsEnabled = enabled) }
    fun setBigNightThreshold(points: Int) = mutate { it.copy(bigNightThreshold = BigNightAlerts.clamp(points)) }
    fun setAlternativeThreshold(points: Int) = mutate { it.copy(alternativeThreshold = points) }
    fun setNightVision(enabled: Boolean) = mutate { it.copy(nightVision = enabled) }
    fun setUnits(units: UnitSystem) = mutate { it.copy(units = units) }
    fun setIssEnabled(enabled: Boolean) = mutate { it.copy(issEnabled = enabled) }
    fun setAuroraAlertsEnabled(enabled: Boolean) = mutate { it.copy(auroraAlertsEnabled = enabled) }
    fun setAuroraKpThreshold(kp: Int) = mutate { it.copy(auroraKpThreshold = AuroraAlerts.clamp(kp)) }

    /** Flips one card. The set lives in settings, so it survives restarts and applies to every site. */
    fun toggleCardCollapsed(cardId: String) = mutate {
        it.copy(collapsedCards = if (cardId in it.collapsedCards) it.collapsedCards - cardId else it.collapsedCards + cardId)
    }

    fun logSession(
        siteId: String,
        date: LocalDate,
        score: Int?,
        rating: Int,
        note: String,
        report: NightReport? = null,
    ) {
        val matching = report?.takeIf { it.site.id == siteId && it.date == date }
        val facts = matching?.let {
            SessionCapture.from(
                score = it.scoreValue,
                bortle = it.site.effectiveBortle,
                issPassCount = it.issPasses.size,
                overnightLowC = it.dew?.overnightLowC,
            )
        }
        val entry = SessionEntry(
            siteId = siteId,
            nightDate = date.toString(),
            score = facts?.score ?: score,
            rating = rating,
            note = note.trim(),
            createdAtEpochSec = java.time.Instant.now().epochSecond,
            bortle = facts?.bortle ?: state.value?.sites?.get(siteId)?.effectiveBortle,
            issVisible = facts?.issVisible,
            overnightLowC = facts?.overnightLowC,
        )
        mutate {
            if ("${entry.siteId}/${entry.nightDate}" in SessionPrompt.loggedKeys(it.sessions)) it
            else it.copy(sessions = it.sessions + entry)
        }
    }

    fun markPassportStampsSeen(ids: Set<String>) {
        if (ids.isEmpty()) return
        mutate { current ->
            val merged = current.seenPassportStamps + ids
            if (merged == current.seenPassportStamps) current else current.copy(seenPassportStamps = merged)
        }
    }

    fun dismissSessionPrompt(siteId: String, date: LocalDate) {
        val key = SessionPrompt.key(siteId, date)
        mutate { if (key in it.dismissedSessionPrompts) it else it.copy(dismissedSessionPrompts = it.dismissedSessionPrompts + key) }
    }

    /**
     * Exports sites and gear to JSON off the main thread. Returns null when there is no state yet.
     */
    suspend fun exportLibrary(): String? = withContext(Dispatchers.Default) {
        state.value?.let(LibraryTransfer::encode)
    }

    /**
     * Imports a library from [uri]. Reading and parsing run off the main thread, with a size bound
     * on the document. Success and failure are published to [libraryNote].
     */
    fun importLibrary(uri: Uri) {
        viewModelScope.launch {
            _libraryBusy.value = true
            _libraryNote.value = null
            try {
                val text = withContext(Dispatchers.IO) { readLibraryText(uri) }
                val file = withContext(Dispatchers.Default) { LibraryTransfer.decode(text) }
                mutate { LibraryTransfer.apply(it, file) }
                _libraryNote.value = getApplication<Application>().getString(R.string.library_imported)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: EmptyLibraryException) {
                _libraryNote.value = getApplication<Application>().getString(R.string.library_import_empty)
            } catch (e: LibraryFormatException) {
                _libraryNote.value = getApplication<Application>().getString(R.string.library_import_failed)
            } catch (e: Exception) {
                _libraryNote.value = getApplication<Application>().getString(R.string.library_import_failed)
            } finally {
                _libraryBusy.value = false
            }
        }
    }

    private suspend fun readLibraryText(uri: Uri): String {
        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw LibraryFormatException("cannot read that file")
        return input.use { stream -> LibraryTransfer.readBounded(stream) }
    }

    fun sendDigestNow() {
        val s = state.value ?: return
        DigestScheduler.runNow(getApplication(), siteIds = listOfNotNull(s.sites.primaryId))
    }

    fun rescheduleDigest() {
        viewModelScope.launch { DigestScheduler.reschedule(getApplication()) }
    }

    private companion object {
        const val ONBOARDING_SITE = "onboarding.site"
        const val ONBOARDING_GEAR = "onboarding.gear"
        const val UPDATE_CHECK_INTERVAL_SEC = 2L * 60L * 60L
    }

    private fun mutate(transform: (AppState) -> AppState) {
        viewModelScope.launch {
            val previousSites = graph.settings.current().sites
            val newState = graph.settings.update(transform)
            DigestScheduler.reschedule(getApplication(), newState)
            if (previousSites != newState.sites) WidgetRefresh.request(getApplication())
        }
    }
}

/** A saved site this close is the same darker-sky recommendation, even after a restart gives it a new id. */
internal const val SAME_DARK_SKY_KM = 1.0

/** Candidate ids that [book] already contains as a saved site. Identity is the location, not the candidate id. */
internal fun savedDarkSkyIds(book: SiteBook, candidates: List<DarkSkyCandidate>): Set<String> =
    candidates.mapNotNull { candidate ->
        val spot = candidate.site
        val already = book.sites.any {
            DarkSkyFinder.distanceKm(it.latitude, it.longitude, spot.latitude, spot.longitude) < SAME_DARK_SKY_KM
        }
        if (already) candidate.site.id else null
    }.toSet()
