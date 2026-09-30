package dev.peterdsp.odivrelo.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.peterdsp.odivrelo.BuildConfig
import dev.peterdsp.odivrelo.R
import dev.peterdsp.odivrelo.core.Brand
import dev.peterdsp.odivrelo.features.settings.AppDataMode
import dev.peterdsp.odivrelo.features.settings.Appearance
import dev.peterdsp.odivrelo.features.settings.OdivreloSettings
import dev.peterdsp.odivrelo.state.AppState
import dev.peterdsp.odivrelo.state.OdivreloViewModel
import dev.peterdsp.odivrelo.theme.Space
import dev.peterdsp.odivrelo.ui.Routes
import dev.peterdsp.odivrelo.ui.adaptive.LocalOdivreloWindow
import dev.peterdsp.odivrelo.ui.booking.Handoff
import dev.peterdsp.odivrelo.ui.common.Badge
import dev.peterdsp.odivrelo.ui.common.DemoNotice
import dev.peterdsp.odivrelo.ui.common.Formats
import dev.peterdsp.odivrelo.ui.common.InfoPanel
import dev.peterdsp.odivrelo.ui.common.KeyValueRow
import dev.peterdsp.odivrelo.ui.common.LoadableContent
import dev.peterdsp.odivrelo.ui.common.OutcomeState
import dev.peterdsp.odivrelo.ui.common.OutlinedBox
import dev.peterdsp.odivrelo.ui.common.OdivreloButton
import dev.peterdsp.odivrelo.ui.common.OdivreloScreen
import dev.peterdsp.odivrelo.ui.common.ReadingColumn
import dev.peterdsp.odivrelo.ui.common.RestrictedPanel
import dev.peterdsp.odivrelo.ui.common.RightsBadge
import dev.peterdsp.odivrelo.ui.common.SectionTitle
import dev.peterdsp.odivrelo.ui.common.Tone
import dev.peterdsp.odivrelo.ui.currentLocale

/**
 * The settings list: language, appearance, accessibility, storage, and the way
 * into privacy, sources, support, licences and diagnostics.
 */
@Composable
fun SettingsListScreen(
    state: AppState,
    viewModel: OdivreloViewModel,
    selectedRoute: String?,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    val locale = currentLocale()
    val window = LocalOdivreloWindow.current

    LaunchedEffect(Unit) { viewModel.refreshRelease() }

    OdivreloScreen(
        title = stringResource(R.string.settings_title),
        modifier = modifier,
        constrainWidth = window.paneCount == 1,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x4),
        ) {
            if (state.isDemo) {
                Spacer(Modifier.height(Space.x2))
                DemoNotice()
            }

            SectionTitle(stringResource(R.string.settings_language))
            Column(Modifier.selectableGroup()) {
                Brand.LANGUAGES.forEach { tag ->
                    val label = stringResource(
                        when (tag) {
                            "el" -> R.string.language_el
                            "en" -> R.string.language_en
                            else -> R.string.language_sq
                        },
                    )
                    ChoiceRow(
                        label = label,
                        selected = state.settings.resolvedLanguageTag == tag,
                        testTag = "settings-language-" + tag,
                        onClick = { viewModel.setLanguage(tag) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.settings_appearance))
            Column(Modifier.selectableGroup()) {
                Appearance.entries.forEach { appearance ->
                    val label = stringResource(
                        when (appearance) {
                            Appearance.FOLLOW_SYSTEM -> R.string.appearance_system
                            Appearance.LIGHT -> R.string.appearance_light
                            Appearance.DARK -> R.string.appearance_dark
                        },
                    )
                    ChoiceRow(
                        label = label,
                        selected = state.settings.appearance == appearance,
                        testTag = "settings-appearance-" + appearance.name.lowercase(),
                        onClick = { viewModel.setAppearance(appearance) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.settings_accessibility))
            SwitchRow(
                label = stringResource(R.string.settings_larger_targets),
                help = stringResource(R.string.settings_larger_targets_help),
                checked = state.settings.largerTouchTargets,
                testTag = "settings-larger-targets",
                onChange = viewModel::setLargerTouchTargets,
            )
            SwitchRow(
                label = stringResource(R.string.settings_reduce_motion),
                help = stringResource(R.string.settings_reduce_motion_help),
                checked = state.settings.reduceMotion,
                testTag = "settings-reduce-motion",
                onChange = viewModel::setReduceMotion,
            )
            SwitchRow(
                label = stringResource(R.string.settings_expand_stops),
                help = null,
                checked = state.settings.alwaysExpandStops,
                testTag = "settings-expand-stops",
                onChange = viewModel::setAlwaysExpandStops,
            )

            SectionTitle(stringResource(R.string.settings_offline_storage))
            KeyValueRow(
                label = stringResource(R.string.settings_offline_storage),
                value = Formats.bytes(state.installedBytes),
            )
            SwitchRow(
                label = stringResource(R.string.settings_download_metered),
                help = null,
                checked = state.settings.downloadOverMeteredNetwork,
                testTag = "settings-metered",
                onChange = viewModel::setMeteredDownloads,
            )
            Text(
                text = stringResource(R.string.settings_clear_offline_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.x2))
            OdivreloButton(
                text = stringResource(R.string.settings_clear_offline),
                onClick = viewModel::clearOfflineStorage,
                outlined = true,
                modifier = Modifier.fillMaxWidth().testTag("settings-clear-offline"),
            )

            SectionTitle(stringResource(R.string.settings_data_mode))
            Text(
                text = stringResource(R.string.data_mode_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                AppDataMode.entries.forEach { mode ->
                    ChoiceRow(
                        label = stringResource(
                            if (mode == AppDataMode.DEMO) {
                                R.string.data_mode_demo
                            } else {
                                R.string.data_mode_real
                            },
                        ),
                        selected = state.settings.dataMode == mode,
                        testTag = "settings-data-mode-" + mode.name.lowercase(),
                        onClick = { viewModel.setDataMode(mode) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.settings_about))
            Routes.settingsDetails.forEach { route ->
                LinkRow(
                    label = stringResource(labelFor(route)),
                    selected = selectedRoute == route,
                    testTag = "settings-open-" + route.substringAfterLast('/'),
                    onClick = { onOpen(route) },
                )
            }

            Spacer(Modifier.height(Space.x5))
            KeyValueRow(
                label = stringResource(R.string.settings_version),
                value = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
            )
            KeyValueRow(
                label = stringResource(R.string.settings_commit),
                value = BuildConfig.GIT_COMMIT,
            )
            KeyValueRow(
                label = stringResource(R.string.settings_release_id),
                value = state.meta.valueOrNull?.releaseId
                    ?: stringResource(R.string.state_no_data_title),
            )
            state.meta.valueOrNull?.publishedAt?.let { published ->
                KeyValueRow(
                    label = stringResource(R.string.settings_data_release_title),
                    value = Formats.timestamp(published, locale).orEmpty(),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x3),
                modifier = Modifier.padding(top = Space.x3),
            ) {
                // The mark's meaning is the text beside it, so the image itself
                // is decorative to TalkBack.
                Image(
                    painter = painterResource(R.drawable.ic_brand_mark),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
                Text(
                    text = stringResource(R.string.settings_about_mark),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.settings_about_name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.x2),
            )
            Spacer(Modifier.height(Space.x10))
        }
    }
}

private fun labelFor(route: String): Int = when (route) {
    Routes.SETTINGS_NOTIFICATIONS -> R.string.settings_notifications
    Routes.SETTINGS_PRIVACY -> R.string.settings_privacy
    Routes.SETTINGS_SOURCES -> R.string.settings_data_sources
    Routes.SETTINGS_COVERAGE -> R.string.operator_coverage_title
    Routes.SETTINGS_SUPPORT -> R.string.settings_support
    Routes.SETTINGS_LICENCES -> R.string.settings_licences
    else -> R.string.settings_diagnostics
}

/** What the detail pane shows before a section has been chosen. */
@Composable
fun SettingsPlaceholder() {
    OutcomeState(
        title = stringResource(R.string.settings_title),
        body = stringResource(R.string.settings_privacy_body),
        isError = false,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(
    label: String,
    help: String?,
    checked: Boolean,
    testTag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .padding(vertical = Space.x2)
            .semantics(mergeDescendants = true) {
                contentDescription = label + (help?.let { ". " + it } ?: "")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (help != null) {
                Text(
                    text = help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(testTag))
    }
}

@Composable
private fun LinkRow(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = Space.x2)
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                this.selected = selected
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/**
 * The privacy policy, translated from the canonical document rather than
 * paraphrased. Every honest line in it is here, including the ones that make
 * the product look less capable than a competitor's marketing.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    OdivreloScreen(title = stringResource(R.string.settings_privacy), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            ReadingColumn {
                Text(
                    text = stringResource(R.string.privacy_version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = context.getString(R.string.privacy_contact, Brand.SUPPORT_EMAIL),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Section(R.string.privacy_short_title, R.string.privacy_short_body)

                SectionTitle(stringResource(R.string.privacy_storage_title))
                Body(R.string.privacy_storage_intro)
                Bullet(R.string.privacy_storage_preferences)
                Bullet(R.string.privacy_storage_recents)
                Bullet(R.string.privacy_storage_saved)
                Bullet(R.string.privacy_storage_packs)
                Bullet(R.string.privacy_storage_tickets)
                Bullet(R.string.privacy_storage_reminders)
                Spacer(Modifier.height(Space.x2))
                InfoPanel(
                    title = stringResource(R.string.privacy_storage_footer),
                    body = null,
                    tone = Tone.SUCCESS,
                )

                Section(R.string.privacy_wallet_title, R.string.privacy_wallet_body)

                SectionTitle(stringResource(R.string.privacy_network_title))
                Bullet(R.string.privacy_network_packs)
                Bullet(R.string.privacy_network_booking)
                Bullet(R.string.privacy_network_maps)
                Spacer(Modifier.height(Space.x2))
                Body(R.string.privacy_network_footer)

                SectionTitle(stringResource(R.string.privacy_permissions_title))
                Bullet(R.string.privacy_permissions_notifications)
                Bullet(R.string.privacy_permissions_files)
                Spacer(Modifier.height(Space.x2))
                Body(R.string.privacy_permissions_none)

                Section(
                    R.string.privacy_notifications_title,
                    R.string.privacy_notifications_body,
                )
                Section(
                    R.string.privacy_third_parties_title,
                    R.string.privacy_third_parties_body,
                )
                Section(R.string.privacy_children_title, R.string.privacy_children_body)

                SectionTitle(stringResource(R.string.privacy_service_data_title))
                Text(
                    text = context.getString(
                        R.string.privacy_service_data_body,
                        Brand.SUPPORT_EMAIL,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Section(R.string.privacy_changes_title, R.string.privacy_changes_body)
                Spacer(Modifier.height(Space.x10))
            }
        }
    }
}

@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    OdivreloScreen(title = stringResource(R.string.settings_support), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            ReadingColumn {
                InfoPanel(
                    title = stringResource(R.string.support_demo_title),
                    body = stringResource(R.string.support_demo_body),
                    tone = Tone.WARNING,
                    modifier = Modifier.testTag("support-demo"),
                )

                SectionTitle(stringResource(R.string.support_questions_title))
                Question(R.string.support_q_missing, R.string.support_a_missing)
                Question(R.string.support_q_tickets, R.string.support_a_tickets)
                Question(R.string.support_q_freshness, R.string.support_a_freshness)
                Question(R.string.support_q_map, R.string.support_a_map)
                Question(R.string.support_q_live, R.string.support_a_live)
                Question(R.string.support_q_pack, R.string.support_a_pack)
                Question(R.string.support_q_delete, R.string.support_a_delete)

                SectionTitle(stringResource(R.string.support_report_title))
                Text(
                    text = context.getString(R.string.support_report_body, Brand.SUPPORT_EMAIL),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Space.x3))
                InfoPanel(
                    title = stringResource(R.string.support_report_warning),
                    body = null,
                    tone = Tone.WARNING,
                )
                Spacer(Modifier.height(Space.x3))
                OdivreloButton(
                    text = stringResource(R.string.support_email_action),
                    onClick = {
                        Handoff.email(
                            context = context,
                            address = Brand.SUPPORT_EMAIL,
                            subject = context.getString(
                                R.string.support_email_subject,
                                BuildConfig.VERSION_NAME + " " + BuildConfig.GIT_COMMIT,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("support-email"),
                )

                SectionTitle(stringResource(R.string.support_corrections_title))
                Text(
                    text = context.getString(
                        R.string.support_corrections_body,
                        Brand.SUPPORT_EMAIL,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Section(
                    R.string.support_accessibility_title,
                    R.string.support_accessibility_body,
                )
                Spacer(Modifier.height(Space.x10))
            }
        }
    }
}

@Composable
fun SourcesScreen(state: AppState, viewModel: OdivreloViewModel, onBack: () -> Unit) {
    val locale = currentLocale()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refreshRelease() }

    OdivreloScreen(
        title = stringResource(R.string.sources_title),
        onBack = onBack,
    ) { padding ->
        LoadableContent(
            value = state.sources,
            modifier = Modifier.fillMaxSize().padding(padding),
            onRetry = viewModel::refreshRelease,
        ) { sources ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("sources-list"),
                contentPadding = PaddingValues(Space.x4),
                verticalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                item(key = "intro") {
                    Text(
                        text = stringResource(R.string.sources_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                items(sources.sources, key = { it.id }) { source ->
                    OutlinedBox {
                        Column {
                            Text(source.name, style = MaterialTheme.typography.titleSmall)
                            if (!source.rightsStatus.isPublishable) {
                                Spacer(Modifier.height(Space.x2))
                                RestrictedPanel()
                            }
                            source.url?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(Space.x2))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                                RightsBadge(source.rightsStatus)
                                source.licence?.let {
                                    Badge(
                                        context.getString(R.string.sources_licence, it),
                                        Tone.NEUTRAL,
                                    )
                                }
                            }
                            Formats.timestamp(source.retrievedAt, locale)?.let {
                                Spacer(Modifier.height(Space.x1))
                                Text(
                                    text = context.getString(R.string.sources_retrieved, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            source.note?.let {
                                Spacer(Modifier.height(Space.x2))
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoverageScreen(state: AppState, viewModel: OdivreloViewModel, onBack: () -> Unit) {
    val language = state.settings.resolvedLanguageTag
    val locale = currentLocale()
    LaunchedEffect(Unit) { viewModel.refreshRelease() }

    OdivreloScreen(
        title = stringResource(R.string.operator_coverage_title),
        onBack = onBack,
    ) { padding ->
        LoadableContent(
            value = state.coverage,
            modifier = Modifier.fillMaxSize().padding(padding),
            onRetry = viewModel::refreshRelease,
        ) { coverage ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            ) {
                ReadingColumn {
                    if (state.isDemo) {
                        Spacer(Modifier.height(Space.x2))
                        DemoNotice()
                    }
                    Spacer(Modifier.height(Space.x3))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                        dev.peterdsp.odivrelo.ui.common.CoverageBadge(coverage.state)
                    }
                    coverage.note?.resolve(language)?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(Space.x3))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }

                    // The span of dates this release describes. Every date
                    // inside it is described; not every date inside it has a
                    // published timetable, and that difference is the whole
                    // reason the search can answer "no offline data for this
                    // date" without claiming anything about service.
                    coverage.serviceDates?.let { range ->
                        val from = range.from
                        val to = range.to
                        if (from != null && to != null) {
                            KeyValueRow(
                                label = stringResource(R.string.search_date_label),
                                value = Formats.serviceDate(from, locale) + " – " +
                                    Formats.serviceDate(to, locale),
                            )
                        }
                    }
                    KeyValueRow(
                        label = stringResource(R.string.operator_routes_count, 0)
                            .replace("0 ", ""),
                        value = coverage.corridorCount.toString(),
                    )
                    KeyValueRow(
                        label = stringResource(R.string.journey_operator),
                        value = coverage.operatorCount.toString(),
                    )
                    KeyValueRow(
                        label = stringResource(R.string.results_title),
                        value = coverage.journeyCount.toString(),
                    )

                    if (coverage.notCovered.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.coverage_not_covered_title))
                        coverage.notCovered.forEach { line ->
                            Text(
                                text = "• " + line.resolve(language),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = Space.x1),
                            )
                        }
                    }

                    // The publisher's own words about what an absent journey
                    // means. A client must never turn "we have no record" into
                    // "there is no service", so this wording is never invented
                    // here.
                    if (coverage.absenceSemantics.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.coverage_absence_title))
                        coverage.absenceSemantics.forEach { (key, value) ->
                            KeyValueRow(label = key, value = value)
                        }
                    }

                    if (coverage.operators.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.journey_operator))
                        coverage.operators.forEach { summary ->
                            KeyValueRow(
                                label = summary.name?.resolve(language)
                                    ?: summary.operatorId.orEmpty(),
                                value = summary.journeyCount.toString(),
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.x10))
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(state: AppState, viewModel: OdivreloViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val locale = currentLocale()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshNotificationPermission() }

    OdivreloScreen(
        title = stringResource(R.string.settings_notifications),
        onBack = onBack,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            ReadingColumn {
                Body(R.string.reminders_body)
                Spacer(Modifier.height(Space.x2))
                Body(R.string.reminders_privacy_note)

                Spacer(Modifier.height(Space.x4))
                InfoPanel(
                    title = stringResource(
                        if (state.notificationsPermitted) {
                            R.string.rights_allowed
                        } else {
                            R.string.state_permission_denied_title
                        },
                    ),
                    body = if (state.notificationsPermitted) {
                        null
                    } else {
                        stringResource(R.string.reminders_permission_body)
                    },
                    tone = if (state.notificationsPermitted) Tone.SUCCESS else Tone.WARNING,
                    modifier = Modifier.testTag("notifications-permission-state"),
                ) {
                    if (!state.notificationsPermitted) {
                        Column {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                OdivreloButton(
                                    text = stringResource(R.string.reminders_permission_grant),
                                    onClick = {
                                        permissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("notifications-grant"),
                                )
                                Spacer(Modifier.height(Space.x2))
                            }
                            OdivreloButton(
                                text = stringResource(R.string.reminders_permission_settings),
                                onClick = { Handoff.openSystemNotificationSettings(context) },
                                outlined = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                SectionTitle(stringResource(R.string.reminders_lead_label))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    OdivreloSettings.REMINDER_LEAD_CHOICES.forEach { minutes ->
                        androidx.compose.material3.FilterChip(
                            selected = state.settings.reminderLeadMinutes == minutes,
                            onClick = { viewModel.setReminderLead(minutes) },
                            label = { Text(minutes.toString()) },
                            modifier = Modifier
                                .heightIn(min = Space.touchTarget)
                                .testTag("lead-" + minutes),
                        )
                    }
                }

                SectionTitle(stringResource(R.string.reminders_title))
                if (state.reminders.isEmpty()) {
                    Body(R.string.reminders_cancelled)
                } else {
                    state.reminders.forEach { reminder ->
                        OutlinedBox(modifier = Modifier.padding(vertical = Space.x1)) {
                            Column {
                                Text(
                                    text = reminder.boardingLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = context.getString(
                                        R.string.reminders_scheduled,
                                        reminder.departureLabel,
                                    ) + " · " + Formats.serviceDate(reminder.serviceDate, locale),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(Space.x2))
                                OdivreloButton(
                                    text = stringResource(R.string.action_cancel),
                                    onClick = { viewModel.cancelReminder(reminder.savedTripId) },
                                    outlined = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.x2))
                Body(R.string.reminders_timezone_changed)
                Spacer(Modifier.height(Space.x10))
            }
        }
    }
}

/**
 * The open-source notices.
 *
 * The list is the dependency set declared in the version catalogue, which is
 * the whole of it: there is no analytics, advertising, crash-reporting or
 * social library to leave out.
 */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    OdivreloScreen(title = stringResource(R.string.licences_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("licences-list"),
            contentPadding = PaddingValues(Space.x4),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            item(key = "intro") {
                Column {
                    Text(
                        text = stringResource(R.string.licences_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SectionTitle(stringResource(R.string.settings_open_source_title))
                }
            }
            items(OPEN_SOURCE, key = { it.first }) { (name, licence) ->
                KeyValueRow(label = name, value = licence)
            }
            item(key = "notice") {
                Spacer(Modifier.height(Space.x4))
                Text(
                    text = "Odivrelo · " + Brand.REPOSITORY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(state: AppState, viewModel: OdivreloViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val window = LocalOdivreloWindow.current
    var copied by rememberSaveable { mutableStateOf(false) }
    val text = remember(state, window) {
        viewModel.diagnosticsText(window.label, window.postureLabel)
    }

    OdivreloScreen(
        title = stringResource(R.string.diagnostics_title),
        onBack = onBack,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            ReadingColumn {
                Body(R.string.diagnostics_body)
                Spacer(Modifier.height(Space.x3))
                KeyValueRow(
                    label = stringResource(R.string.diagnostics_window),
                    value = stringResource(
                        when (window.label) {
                            "compact" -> R.string.window_compact
                            "medium" -> R.string.window_medium
                            else -> R.string.window_expanded
                        },
                    ),
                )
                KeyValueRow(
                    label = stringResource(R.string.diagnostics_posture),
                    value = stringResource(
                        when (window.postureLabel) {
                            "tabletop" -> R.string.posture_tabletop
                            "book" -> R.string.posture_book
                            "half-opened" -> R.string.posture_half_open
                            "flat-with-hinge" -> R.string.posture_flat
                            else -> R.string.posture_none
                        },
                    ),
                )
                KeyValueRow(
                    label = stringResource(R.string.diagnostics_packs),
                    value = (state.catalog.valueOrNull?.installed?.size ?: 0).toString(),
                )
                Spacer(Modifier.height(Space.x3))
                OutlinedBox(modifier = Modifier.testTag("diagnostics-text")) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
                Spacer(Modifier.height(Space.x3))
                OdivreloButton(
                    text = stringResource(R.string.action_copy),
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText("Odivrelo diagnostics", text),
                        )
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("diagnostics-copy"),
                )
                if (copied) {
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        text = stringResource(R.string.diagnostics_copied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Space.x3))
                Text(
                    text = stringResource(R.string.settings_signing_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Space.x10))
            }
        }
    }
}

@Composable
private fun Section(title: Int, body: Int) {
    SectionTitle(stringResource(title))
    Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Body(body: Int) {
    Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Bullet(body: Int) {
    Text(
        text = "• " + stringResource(body),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = Space.x1),
    )
}

@Composable
private fun Question(question: Int, answer: Int) {
    Column(Modifier.padding(vertical = Space.x2)) {
        Text(
            text = stringResource(question),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Space.x1))
        Text(text = stringResource(answer), style = MaterialTheme.typography.bodyMedium)
    }
}

private val OPEN_SOURCE: List<Pair<String, String>> = listOf(
    "Kotlin standard library" to "Apache-2.0",
    "kotlinx.coroutines" to "Apache-2.0",
    "kotlinx.serialization" to "Apache-2.0",
    "kotlinx-datetime" to "Apache-2.0",
    "Ktor client" to "Apache-2.0",
    "OkHttp" to "Apache-2.0",
    "SQLDelight" to "Apache-2.0",
    "AndroidX Core" to "Apache-2.0",
    "AndroidX Activity" to "Apache-2.0",
    "AndroidX Lifecycle" to "Apache-2.0",
    "AndroidX Navigation" to "Apache-2.0",
    "AndroidX Window" to "Apache-2.0",
    "AndroidX Browser" to "Apache-2.0",
    "AndroidX Security Crypto" to "Apache-2.0",
    "AndroidX WorkManager" to "Apache-2.0",
    "AndroidX DataStore" to "Apache-2.0",
    "AndroidX DocumentFile" to "Apache-2.0",
    "AndroidX Core SplashScreen" to "Apache-2.0",
    "Jetpack Compose" to "Apache-2.0",
    "Material Components for Android" to "Apache-2.0",
    "Tink" to "Apache-2.0",
)
