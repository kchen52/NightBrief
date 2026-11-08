package app.nightbrief.app.ui.sites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nightbrief.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightbrief.app.AppViewModel
import app.nightbrief.app.SiteDraft
import app.nightbrief.app.ui.common.Format
import app.nightbrief.app.ui.common.ScorePill
import app.nightbrief.app.ui.common.SectionCard
import app.nightbrief.app.ui.theme.NightColors
import app.nightbrief.sites.BortleSource
import app.nightbrief.sites.Site
import app.nightbrief.score.NightPlanCard
import app.nightbrief.score.NightReport
import androidx.compose.foundation.clickable
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(vm: AppViewModel, onEdit: (String?) -> Unit, contentPadding: PaddingValues) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ui by vm.briefing.collectAsStateWithLifecycle()
    val book = state?.sites ?: return
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    // Delete is immediate; Undo restores the book only if nothing else changed it meanwhile.
    val deleteSite: (Site) -> Unit = { site ->
        val before = book
        vm.deleteSite(site.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = resources.getString(R.string.site_deleted, site.name),
                actionLabel = resources.getString(R.string.undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) vm.restoreSites(before, before.delete(site.id))
        }
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_sites)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            val addSite = stringResource(R.string.add_site)
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(addSite) },
                modifier = Modifier.semantics { contentDescription = addSite },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.padding(inner).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(book.sites, key = { _, s -> s.id }) { index, site ->
                val isPrimary = site.id == book.primaryId
                val report = ui.briefing?.reportFor(site.id)
                val editLabel = stringResource(R.string.edit_named, site.name)
                SectionCard(
                    // The whole card opens the editor; the icon row below stays for TalkBack and reordering.
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = editLabel) { onEdit(site.id) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.setPrimary(site.id) }) {
                            if (isPrimary) Icon(Icons.Filled.Star, stringResource(R.string.primary_site), tint = NightColors.Amber)
                            else Icon(Icons.Outlined.StarOutline, stringResource(R.string.make_primary))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(site.name, style = MaterialTheme.typography.titleMedium)
                            if (isPrimary) {
                                Text(
                                    stringResource(R.string.primary_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NightColors.Amber,
                                )
                            }
                            Text(
                                tonightLine(report),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                bortleLabel(site) + digestSuffix(site, isPrimary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ScorePill(report?.scoreValue)
                    }
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.moveSite(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up_named, site.name))
                        }
                        IconButton(onClick = { vm.moveSite(index, index + 1) }, enabled = index < book.sites.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down_named, site.name))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onEdit(site.id) }) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.edit_named, site.name))
                        }
                        IconButton(onClick = { deleteSite(site) }, enabled = book.sites.size > 1) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete_named, site.name))
                        }
                    }
                }
            }
        }
    }

}

/** Verdict and best window from the cached briefing, the same score Tonight shows. */
@Composable
private fun tonightLine(report: NightReport?): String {
    val score = report?.score ?: return stringResource(R.string.site_no_forecast)
    val window = score.bestWindow?.takeIf { NightPlanCard.showsBestWindow(score) }
    return if (window == null) score.verdict.label
    else stringResource(R.string.site_tonight_line, score.verdict.label, Format.window(window, report.site.zone))
}

/** Only the primary site, or a site with its own time, sends a digest. Say so rather than leave it implied. */
@Composable
private fun digestSuffix(site: Site, isPrimary: Boolean): String = when {
    site.digestTimeOverride != null -> stringResource(R.string.site_digest_suffix, site.digestTimeOverride!!)
    isPrimary -> ""
    else -> stringResource(R.string.site_no_digest_suffix)
}

@Composable
private fun bortleLabel(site: Site): String {
    val bortle = site.bortle ?: return stringResource(R.string.bortle_not_set)
    return if (site.bortleSource == BortleSource.MAP) stringResource(R.string.bortle_map, bortle)
    else stringResource(R.string.bortle_value, bortle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteEditorScreen(vm: AppViewModel, siteId: String?, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val existing = siteId?.let { s.sites[it] }
    var draft by rememberSaveable(siteId, stateSaver = SiteDraftSaver) {
        mutableStateOf(existing?.let { SiteDraft.from(it, it.id == s.sites.primaryId) } ?: SiteDraft())
    }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val saveSite = {
        if (!draft.isValid) {
            showErrors = true
        } else {
            vm.saveSite(draft)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.add_site else R.string.edit_site)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    TextButton(onClick = saveSite) { Text(stringResource(R.string.save_site)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showErrors) {
                draft.errors.firstOrNull()?.let {
                    Text(stringResource(it), color = NightColors.Marginal, style = MaterialTheme.typography.bodySmall)
                }
            }
            SiteForm(
                draft = draft,
                onChange = { draft = it },
                lookupBortle = vm::lookupBortle,
                lookupTimeZone = vm::lookupTimeZone,
                searchPlaces = vm::searchPlaces,
                showPrimaryToggle = existing == null || existing.id != s.sites.primaryId,
                showDigestOverride = true,
                globalDigestTime = s.digestTime,
            )
            Button(
                onClick = saveSite,
                modifier = Modifier.padding(bottom = 24.dp).fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.save_site)) }
        }
    }
}
