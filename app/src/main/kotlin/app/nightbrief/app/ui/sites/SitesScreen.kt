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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(vm: AppViewModel, onEdit: (String?) -> Unit, contentPadding: PaddingValues) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ui by vm.briefing.collectAsStateWithLifecycle()
    val book = state?.sites ?: return
    var pendingDelete by remember { mutableStateOf<Site?>(null) }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_sites)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_site)) },
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
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.setPrimary(site.id) }) {
                            if (isPrimary) Icon(Icons.Filled.Star, stringResource(R.string.primary_site), tint = NightColors.Amber)
                            else Icon(Icons.Outlined.StarOutline, stringResource(R.string.make_primary))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(site.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                Format.coordinates(site.latitude, site.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                bortleLabel(site) + (site.digestTimeOverride?.let { stringResource(R.string.site_digest_suffix, it) } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ScorePill(ui.briefing?.reportFor(site.id)?.scoreValue)
                    }
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.moveSite(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up))
                        }
                        IconButton(onClick = { vm.moveSite(index, index + 1) }, enabled = index < book.sites.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onEdit(site.id) }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                        IconButton(onClick = { pendingDelete = site }, enabled = book.sites.size > 1) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { site ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_site_title, site.name)) },
            text = {
                Text(
                    if (site.id == book.primaryId) stringResource(R.string.delete_primary_body)
                    else stringResource(R.string.delete_site_body),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSite(site.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.add_site else R.string.edit_site)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
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
            SiteForm(
                draft = draft,
                onChange = { draft = it },
                lookupBortle = vm::lookupBortle,
                lookupTimeZone = vm::lookupTimeZone,
                showPrimaryToggle = existing == null || existing.id != s.sites.primaryId,
                showDigestOverride = true,
                globalDigestTime = s.digestTime,
            )
            draft.errors.firstOrNull()?.let {
                Text(stringResource(it), color = NightColors.Marginal, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    vm.saveSite(draft)
                    onDone()
                },
                enabled = draft.isValid,
                modifier = Modifier.padding(bottom = 24.dp).fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.save_site)) }
        }
    }
}
