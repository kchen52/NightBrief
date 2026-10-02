package app.nightbrief.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.nightbrief.data.AppGraph
import app.nightbrief.data.AppState
import app.nightbrief.data.LibraryTransfer
import app.nightbrief.gear.GearKit
import app.nightbrief.score.Briefing
import app.nightbrief.score.NightReport
import app.nightbrief.work.DigestScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.time.LocalDate

data class BriefingUiState(
    val briefing: Briefing? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class AppViewModel(
    app: Application,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : AndroidViewModel(app) {
    private val graph = AppGraph.get(app)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val state: StateFlow<AppState?> = graph.settings.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _briefing = MutableStateFlow(BriefingUiState())
    val briefing: StateFlow<BriefingUiState> = _briefing.asStateFlow()

    private val _selectedSiteId = MutableStateFlow<String?>(null)
    val selectedSiteId: StateFlow<String?> = _selectedSiteId.asStateFlow()

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
                .map { Triple(it.onboardingComplete, it.sites, it.gear) }
                .distinctUntilChanged()
                .collectLatest { (done, _, _) -> if (done) refresh(force = false) }
        }
    }

    fun refresh(force: Boolean) {
        val s = state.value ?: return
        if (s.sites.sites.isEmpty()) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _briefing.update { it.copy(loading = true, error = null) }
            try {
                val result = withContext(Dispatchers.Default) {
                    graph.briefings.brief(s.sites.primaryFirst(), s.gear, forceRefresh = force)
                }
                _briefing.value = BriefingUiState(result, loading = false)
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
        return withContext(Dispatchers.Default) { graph.briefings.plan(site, date, s.gear) }
    }

    /** Bortle class from the bundled light-pollution grid, or null when those grids have no data there. */
    suspend fun lookupBortle(lat: Double, lon: Double): Int? =
        withContext(Dispatchers.IO) { graph.bortleLookup?.lookup(lat, lon) }

    /** IANA time zone from Open-Meteo, or null when the lookup fails. */
    suspend fun lookupTimeZone(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) { graph.timeZoneLookup.zoneFor(lat, lon) }

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
        val raw = savedState.get<String>(ONBOARDING_SITE) ?: return SiteDraft(name = "Home", makePrimary = true)
        return runCatching { json.decodeFromString(SiteDraft.serializer(), raw) }
            .getOrDefault(SiteDraft(name = "Home", makePrimary = true))
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

    fun deleteSite(id: String) = mutate { it.copy(sites = it.sites.delete(id)) }
    fun setPrimary(id: String) = mutate { it.copy(sites = it.sites.setPrimary(id)) }
    fun moveSite(from: Int, to: Int) = mutate { it.copy(sites = it.sites.move(from, to)) }

    fun updateGear(transform: (GearKit) -> GearKit) = mutate { it.copy(gear = transform(it.gear)) }

    fun setDigestTime(time: String) = mutate { it.copy(digestTime = time) }
    fun setDigestEnabled(enabled: Boolean) = mutate { it.copy(digestEnabled = enabled) }
    fun setBigNightAlertsEnabled(enabled: Boolean) = mutate { it.copy(bigNightAlertsEnabled = enabled) }
    fun setAlternativeThreshold(points: Int) = mutate { it.copy(alternativeThreshold = points) }

    fun exportLibrary(): String? = state.value?.let(LibraryTransfer::encode)

    fun importLibrary(json: String) {
        val file = LibraryTransfer.decode(json)
        mutate { LibraryTransfer.apply(it, file) }
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
    }

    private fun mutate(transform: (AppState) -> AppState) {
        viewModelScope.launch {
            val newState = graph.settings.update(transform)
            DigestScheduler.reschedule(getApplication(), newState)
        }
    }
}
