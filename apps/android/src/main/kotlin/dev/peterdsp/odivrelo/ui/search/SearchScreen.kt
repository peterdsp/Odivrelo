package dev.peterdsp.odivrelo.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.peterdsp.odivrelo.R
import dev.peterdsp.odivrelo.core.model.CoverageState
import dev.peterdsp.odivrelo.core.time.ServiceTime
import dev.peterdsp.odivrelo.features.Loadable
import dev.peterdsp.odivrelo.features.search.SearchEndpoint
import dev.peterdsp.odivrelo.state.AppState
import dev.peterdsp.odivrelo.state.OdivreloViewModel
import dev.peterdsp.odivrelo.theme.Space
import dev.peterdsp.odivrelo.ui.common.Badge
import dev.peterdsp.odivrelo.ui.common.CoverageBadge
import dev.peterdsp.odivrelo.ui.common.DemoNotice
import dev.peterdsp.odivrelo.ui.common.Formats
import dev.peterdsp.odivrelo.ui.common.InfoPanel
import dev.peterdsp.odivrelo.ui.common.OdivreloButton
import dev.peterdsp.odivrelo.ui.common.OdivreloScreen
import dev.peterdsp.odivrelo.ui.common.ReadingColumn
import dev.peterdsp.odivrelo.ui.common.SectionTitle
import dev.peterdsp.odivrelo.ui.common.Tone
import dev.peterdsp.odivrelo.ui.currentLocale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * The search form.
 *
 * Origin, destination, a service date in `Europe/Athens` and the filters. The
 * date is never computed here: `ServiceTime` in the core owns every service-date
 * decision, and this screen only shows what it returns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: AppState,
    viewModel: OdivreloViewModel,
    onPickPlace: () -> Unit,
    onRun: () -> Unit,
    onOpenStop: (String) -> Unit,
) {
    val context = LocalContext.current
    val locale = currentLocale()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    OdivreloScreen(title = stringResource(R.string.search_title)) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.x8),
        ) {
            ReadingColumn {
                if (state.isDemo) {
                    Spacer(Modifier.height(Space.x2))
                    DemoNotice()
                }

                Spacer(Modifier.height(Space.x4))

                EndpointRow(
                    label = stringResource(R.string.search_origin_label),
                    value = state.session.query.origin?.label,
                    terminal = state.session.query.origin?.terminalLabel,
                    testTag = "search-origin",
                    onClick = {
                        viewModel.openPlacePicker(SearchEndpoint.ORIGIN)
                        onPickPlace()
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = viewModel::swapEndpoints,
                        modifier = Modifier
                            .heightIn(min = Space.touchTarget)
                            .testTag("search-swap"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = stringResource(R.string.action_swap),
                        )
                    }
                }

                EndpointRow(
                    label = stringResource(R.string.search_destination_label),
                    value = state.session.query.destination?.label,
                    terminal = state.session.query.destination?.terminalLabel,
                    testTag = "search-destination",
                    onClick = {
                        viewModel.openPlacePicker(SearchEndpoint.DESTINATION)
                        onPickPlace()
                    },
                )

                if (state.session.query.isSameEndpoint) {
                    Spacer(Modifier.height(Space.x3))
                    InfoPanel(
                        title = stringResource(R.string.search_same_endpoints),
                        body = null,
                        tone = Tone.WARNING,
                    )
                }

                Spacer(Modifier.height(Space.x6))
                Text(
                    text = stringResource(R.string.search_date_label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Space.x2))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.shiftServiceDate(-1) },
                        enabled = state.session.query.serviceDate > state.today,
                        modifier = Modifier.testTag("search-previous-day"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = stringResource(R.string.action_previous_day),
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Space.touchTarget)
                            .clickable { showDatePicker = true }
                            .testTag("search-date"),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(Space.x3),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = Formats.serviceDateLong(
                                    state.session.query.serviceDate,
                                    locale,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(R.string.search_date_zone_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.shiftServiceDate(1) },
                        modifier = Modifier.testTag("search-next-day"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.action_next_day),
                        )
                    }
                }

                // A restored session can carry yesterday's service date. That is
                // a real state with a real remedy, not something to silently
                // rewrite behind the person's back.
                if (state.session.query.serviceDate < state.today) {
                    Spacer(Modifier.height(Space.x3))
                    InfoPanel(
                        title = stringResource(R.string.state_expired_title),
                        body = stringResource(R.string.state_expired_body),
                        tone = Tone.WARNING,
                        modifier = Modifier.testTag("search-expired-date"),
                    ) {
                        OdivreloButton(
                            text = stringResource(R.string.action_today),
                            onClick = { viewModel.setServiceDate(state.today) },
                            outlined = true,
                        )
                    }
                }

                Spacer(Modifier.height(Space.x2))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    AssistChip(
                        onClick = { viewModel.setServiceDate(state.today) },
                        label = { Text(stringResource(R.string.action_today)) },
                        modifier = Modifier.testTag("search-today"),
                    )
                    if (state.datesWithData.isNotEmpty() &&
                        state.session.query.serviceDate !in state.datesWithData
                    ) {
                        val next = state.datesWithData.firstOrNull { it >= state.today }
                        if (next != null) {
                            AssistChip(
                                onClick = { viewModel.setServiceDate(next) },
                                label = { Text(Formats.serviceDate(next, locale)) },
                                modifier = Modifier.testTag("search-date-with-data"),
                            )
                        }
                    }
                }

                // The installed release only names some dates. Saying which ones
                // is the difference between a person thinking the application is
                // broken and a person knowing exactly what it holds.
                if (state.datesWithData.isNotEmpty() &&
                    state.session.query.serviceDate !in state.datesWithData
                ) {
                    Spacer(Modifier.height(Space.x3))
                    InfoPanel(
                        title = stringResource(R.string.results_empty_no_offline_title),
                        body = stringResource(R.string.results_empty_no_offline_body),
                        tone = Tone.WARNING,
                        modifier = Modifier.testTag("search-no-pack-notice"),
                    )
                }

                Spacer(Modifier.height(Space.x5))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = { showFilters = true },
                        label = {
                            Text(
                                if (state.session.query.filters.isActive) {
                                    context.getString(
                                        R.string.filters_active,
                                        state.session.query.filters.activeCount,
                                    )
                                } else {
                                    context.getString(R.string.action_filters)
                                },
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FilterList, contentDescription = null)
                        },
                        modifier = Modifier
                            .heightIn(min = Space.touchTarget)
                            .testTag("search-filters"),
                    )
                    if (state.session.query.filters.isActive) {
                        TextButton(onClick = viewModel::clearFilters) {
                            Text(stringResource(R.string.action_clear_filters))
                        }
                    }
                }

                Spacer(Modifier.height(Space.x5))
                OdivreloButton(
                    text = stringResource(R.string.search_run),
                    onClick = onRun,
                    enabled = state.session.query.isRunnable,
                    modifier = Modifier.fillMaxWidth().testTag("search-run"),
                )
                if (!state.session.query.isRunnable && !state.session.query.isSameEndpoint) {
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        text = stringResource(R.string.search_needs_both),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                CoverageSummary(state)

                RecentSearches(state, viewModel)

                Spacer(Modifier.height(Space.x8))
            }
        }
    }

    if (showDatePicker) {
        val todayMillis = remember(state.today) {
            LocalDate.parse(state.today).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = remember(state.session.query.serviceDate) {
                LocalDate.parse(state.session.query.serviceDate)
                    .atStartOfDayIn(TimeZone.UTC)
                    .toEpochMilliseconds()
            },
            selectableDates = remember(todayMillis) {
                object : SelectableDates {
                    // A service date in the past cannot be travelled, so it is not
                    // offered. Nothing about it is hidden: the copy says why.
                    override fun isSelectableDate(utcTimeMillis: Long) =
                        utcTimeMillis >= todayMillis
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
                            viewModel.setServiceDate(
                                instant.toString().take(ServiceTime.SERVICE_DATE_LENGTH),
                            )
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("date-confirm"),
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showFilters) {
        FiltersSheet(
            filters = state.session.query.filters,
            operators = state.coverage.valueOrNull?.operators.orEmpty(),
            languageTag = state.settings.resolvedLanguageTag,
            onApply = {
                viewModel.setFilters(it)
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun EndpointRow(
    label: String,
    value: String?,
    terminal: String?,
    testTag: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val shown = value ?: stringResource(R.string.search_endpoint_empty)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                contentDescription = label + ": " + shown
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Space.x4)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = shown, style = MaterialTheme.typography.titleMedium)
            if (!terminal.isNullOrBlank()) {
                Text(
                    text = context.getString(R.string.place_inside_terminal, terminal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CoverageSummary(state: AppState) {
    val coverage = state.coverage.valueOrNull ?: return
    val language = state.settings.resolvedLanguageTag
    Spacer(Modifier.height(Space.x6))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverageBadge(coverage.state)
        if (coverage.journeyCount > 0) {
            Badge(text = coverage.journeyCount.toString() + " ×", tone = Tone.NEUTRAL)
        }
    }
    coverage.note?.resolve(language)?.takeIf { it.isNotBlank() }?.let { note ->
        Spacer(Modifier.height(Space.x2))
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (coverage.state == CoverageState.PARTIAL) {
        Spacer(Modifier.height(Space.x3))
        InfoPanel(
            title = stringResource(R.string.results_partial_coverage_title),
            body = stringResource(R.string.results_partial_coverage_body),
            tone = Tone.WARNING,
        )
    }
}

@Composable
private fun RecentSearches(state: AppState, viewModel: OdivreloViewModel) {
    val locale = currentLocale()
    val language = state.settings.resolvedLanguageTag
    SectionTitle(stringResource(R.string.search_recent_title))
    when (val recents = state.recents) {
        is Loadable.Ready -> recents.value.take(6).forEach { recent ->
            val label = recent.originName.resolve(language) + " → " +
                recent.destinationName.resolve(language)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.touchTarget)
                    .clickable { viewModel.applyRecent(recent) }
                    .padding(vertical = Space.x2)
                    .semantics(mergeDescendants = true) {
                        contentDescription = label + ", " +
                            Formats.serviceDate(recent.serviceDate, locale)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(horizontal = Space.x2))
                Column {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = Formats.serviceDate(recent.serviceDate, locale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        else -> Text(
            text = stringResource(R.string.search_recent_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Space.x2),
        )
    }
}
