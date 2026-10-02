package dev.peterdsp.odivrelo.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import dev.peterdsp.odivrelo.R
import dev.peterdsp.odivrelo.core.model.AvailablePack
import dev.peterdsp.odivrelo.core.model.PackFailure
import dev.peterdsp.odivrelo.core.model.PackPhase
import dev.peterdsp.odivrelo.features.offline.DownloadState
import dev.peterdsp.odivrelo.state.AppState
import dev.peterdsp.odivrelo.state.OdivreloViewModel
import dev.peterdsp.odivrelo.theme.LocalReduceMotion
import dev.peterdsp.odivrelo.theme.Space
import dev.peterdsp.odivrelo.ui.common.Badge
import dev.peterdsp.odivrelo.ui.common.DemoNotice
import dev.peterdsp.odivrelo.ui.common.Formats
import dev.peterdsp.odivrelo.ui.common.InfoPanel
import dev.peterdsp.odivrelo.ui.common.KeyValueRow
import dev.peterdsp.odivrelo.ui.common.LoadableContent
import dev.peterdsp.odivrelo.ui.common.OdivreloButton
import dev.peterdsp.odivrelo.ui.common.OdivreloCard
import dev.peterdsp.odivrelo.ui.common.OdivreloScreen
import dev.peterdsp.odivrelo.ui.common.SectionTitle
import dev.peterdsp.odivrelo.ui.common.Tone
import dev.peterdsp.odivrelo.ui.currentLocale

/**
 * Offline data: what is installed, what can be downloaded, and what offline
 * honestly does not include.
 *
 * Integrity is not a footnote here. A pack is verified against the published
 * SHA-256 before it is installed, a mismatch is refused rather than repaired,
 * and the previous release is kept so a bad update can be rolled back. All of
 * that is visible on this screen rather than buried in a log.
 */
@Composable
fun OfflineScreen(state: AppState, viewModel: OdivreloViewModel) {
    val context = LocalContext.current
    val locale = currentLocale()
    val language = state.settings.resolvedLanguageTag

    LaunchedEffect(Unit) { viewModel.refreshCatalog() }

    OdivreloScreen(title = stringResource(R.string.offline_title)) { padding ->
        LoadableContent(
            value = state.catalog,
            modifier = Modifier.fillMaxSize().padding(padding),
            onRetry = viewModel::refreshCatalog,
        ) { catalog ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("offline-list"),
                contentPadding = PaddingValues(Space.x4),
                verticalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                if (state.isDemo) {
                    item(key = "demo") { DemoNotice() }
                }

                item(key = "release") {
                    Column {
                        KeyValueRow(
                            label = stringResource(R.string.settings_release_id),
                            value = catalog.releaseId,
                        )
                        KeyValueRow(
                            label = stringResource(R.string.offline_published, ""),
                            value = Formats.timestamp(catalog.publishedAt, locale).orEmpty(),
                        )
                        KeyValueRow(
                            label = stringResource(R.string.settings_offline_storage),
                            value = Formats.bytes(catalog.totalInstalledBytes),
                        )
                        if (!catalog.manifestReachable) {
                            Spacer(Modifier.height(Space.x2))
                            InfoPanel(
                                title = stringResource(R.string.offline_manifest_unreachable),
                                body = null,
                                tone = Tone.WARNING,
                                modifier = Modifier.testTag("offline-manifest-unreachable"),
                            )
                        }
                    }
                }

                // The honest statement. There are no base map tiles in this
                // release, and pretending otherwise would strand someone.
                item(key = "maps") {
                    InfoPanel(
                        title = stringResource(R.string.offline_maps_title),
                        body = stringResource(R.string.offline_maps_available) + "\n\n" +
                            stringResource(R.string.offline_maps_unavailable),
                        tone = Tone.INFO,
                        modifier = Modifier.testTag("offline-maps-note"),
                    )
                }

                item(key = "journeys-note") {
                    Text(
                        text = stringResource(R.string.offline_journeys_pack_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item(key = "packs-title") {
                    SectionTitle(stringResource(R.string.offline_title))
                }

                items(catalog.available, key = { it.name }) { pack ->
                    PackRow(
                        pack = pack,
                        languageTag = language,
                        download = state.downloads[pack.name],
                        onDownload = { viewModel.downloadPack(pack.name) },
                        onCancel = { viewModel.cancelDownload(pack.name) },
                        onDismiss = { viewModel.dismissDownload(pack.name) },
                        onRemove = { viewModel.removePack(pack.name) },
                    )
                }

                item(key = "rollback") {
                    Column {
                        SectionTitle(stringResource(R.string.offline_rollback))
                        val target = catalog.rollbackReleaseId
                        if (target == null) {
                            Text(
                                text = stringResource(R.string.offline_rollback_none),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("offline-rollback-none"),
                            )
                        } else {
                            Text(
                                text = context.getString(R.string.offline_rollback_to, target),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(Space.x2))
                            OdivreloButton(
                                text = stringResource(R.string.offline_rollback),
                                onClick = viewModel::rollbackRelease,
                                outlined = true,
                                modifier = Modifier.fillMaxWidth().testTag("offline-rollback"),
                            )
                        }
                    }
                }

                item(key = "gtfs") {
                    Text(
                        text = stringResource(R.string.offline_gtfs_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PackRow(
    pack: AvailablePack,
    languageTag: String,
    download: DownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    OdivreloCard(modifier = Modifier.testTag("pack-" + pack.name)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = pack.title.resolve(languageTag),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = pack.summary.resolve(languageTag),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = Formats.bytes(pack.bytes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            Badge(
                text = stringResource(
                    when {
                        pack.updateAvailable -> R.string.offline_pack_update_available
                        pack.installed -> R.string.offline_pack_installed
                        else -> R.string.offline_pack_not_installed
                    },
                ),
                tone = when {
                    pack.updateAvailable -> Tone.WARNING
                    pack.installed -> Tone.SUCCESS
                    else -> Tone.NEUTRAL
                },
            )
        }

        if (download != null && download.isRunning) {
            Spacer(Modifier.height(Space.x3))
            DownloadProgress(download)
            Spacer(Modifier.height(Space.x2))
            OdivreloButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                outlined = true,
                modifier = Modifier.fillMaxWidth().testTag("pack-cancel-" + pack.name),
            )
        } else {
            Spacer(Modifier.height(Space.x3))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                OdivreloButton(
                    text = stringResource(
                        when {
                            pack.updateAvailable -> R.string.action_update
                            pack.installed -> R.string.action_download
                            else -> R.string.action_download
                        },
                    ),
                    onClick = onDownload,
                    modifier = Modifier.weight(1f).testTag("pack-download-" + pack.name),
                )
                if (pack.installed) {
                    OdivreloButton(
                        text = stringResource(R.string.offline_delete_pack),
                        onClick = onRemove,
                        outlined = true,
                        modifier = Modifier.weight(1f).testTag("pack-delete-" + pack.name),
                    )
                }
            }
        }

        // A finished download that did not succeed stays on screen, naming the
        // reason, until it is dismissed. A transfer that failed silently would
        // be indistinguishable from one that was never started.
        if (download != null && download.finished && !download.succeeded) {
            Spacer(Modifier.height(Space.x3))
            InfoPanel(
                title = context.getString(
                    when (download.failure) {
                        PackFailure.DIGEST_MISMATCH -> R.string.offline_failed_digest
                        PackFailure.RELEASE_MISMATCH -> R.string.offline_failed_release
                        PackFailure.NETWORK, PackFailure.TIMEOUT -> R.string.offline_failed_network
                        PackFailure.STORAGE_FULL -> R.string.state_storage_full_title
                        PackFailure.NOT_IN_MANIFEST -> R.string.offline_failed_missing
                        PackFailure.CANCELLED -> R.string.offline_cancelled
                        else -> R.string.state_unknown_error_title
                    },
                ),
                body = if (download.failure == PackFailure.STORAGE_FULL) {
                    context.getString(R.string.state_storage_full_body)
                } else {
                    null
                },
                tone = if (download.wasCancelled) Tone.NEUTRAL else Tone.ERROR,
                modifier = Modifier.testTag("pack-outcome-" + pack.name),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    if (download.isRetryable) {
                        OdivreloButton(
                            text = stringResource(R.string.action_retry),
                            onClick = onDownload,
                            modifier = Modifier.testTag("pack-retry-" + pack.name),
                        )
                    }
                    OdivreloButton(
                        text = stringResource(R.string.action_close),
                        onClick = onDismiss,
                        outlined = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgress(download: DownloadState) {
    val context = LocalContext.current
    val phase = context.getString(
        when (download.phase) {
            PackPhase.QUEUED -> R.string.offline_phase_queued
            PackPhase.DOWNLOADING -> R.string.offline_phase_downloading
            PackPhase.RESUMING -> R.string.offline_phase_resuming
            PackPhase.VERIFYING -> R.string.offline_phase_verifying
            PackPhase.INSTALLING -> R.string.offline_phase_installing
            PackPhase.DONE -> R.string.offline_phase_done
        },
    )
    val percent = download.percent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = phase + (
                    percent?.let {
                        ", " + context.getString(R.string.a11y_progress, it)
                    } ?: ""
                    )
                if (percent != null) {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = percent.toFloat(),
                        range = 0f..100f,
                    )
                }
            }
            .testTag("pack-progress"),
    ) {
        Text(
            text = if (percent != null) {
                context.getString(R.string.offline_progress, percent, phase)
            } else {
                phase
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(Space.x1))
        val fraction = download.fraction
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (LocalReduceMotion.current) {
            // No declared length and reduced motion is on, so a static bar
            // rather than the animated indeterminate one. It honours the
            // reduced-motion setting and lets the UI-test clock reach idle,
            // exactly as LoadingState does.
            LinearProgressIndicator(progress = { 0.25f }, modifier = Modifier.fillMaxWidth())
        } else {
            // No declared length, so an indeterminate bar rather than a
            // percentage nobody can stand behind.
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (download.isRetrying || download.isResuming) {
            Spacer(Modifier.height(Space.x1))
            Text(
                text = stringResource(R.string.offline_phase_resuming),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
