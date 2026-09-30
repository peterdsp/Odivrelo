package dev.peterdsp.poravia.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.peterdsp.poravia.R
import dev.peterdsp.poravia.core.model.CoverageState
import dev.peterdsp.poravia.core.model.JourneyResults
import dev.peterdsp.poravia.features.EmptyReason
import dev.peterdsp.poravia.state.AppState
import dev.peterdsp.poravia.state.PoraviaViewModel
import dev.peterdsp.poravia.theme.Space
import dev.peterdsp.poravia.ui.adaptive.LocalPoraviaWindow
import dev.peterdsp.poravia.ui.adaptive.TwoPaneLayout
import dev.peterdsp.poravia.ui.common.DemoNotice
import dev.peterdsp.poravia.ui.common.emptyCopy
import dev.peterdsp.poravia.ui.common.Formats
import dev.peterdsp.poravia.ui.common.InfoPanel
import dev.peterdsp.poravia.ui.common.LiveUnavailableNotice
import dev.peterdsp.poravia.ui.common.LoadableContent
import dev.peterdsp.poravia.ui.common.OutcomeState
import dev.peterdsp.poravia.ui.common.PoraviaScreen
import dev.peterdsp.poravia.ui.common.Tone
import dev.peterdsp.poravia.ui.currentLocale
import dev.peterdsp.poravia.ui.journey.JourneyDetailPane
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The result list, and on a wide window the selected journey beside it.
 *
 * One state object drives both panes, so a fold or an unfold changes the
 * geometry and nothing else: the same query, the same service date, the same
 * filters, the same selected journey and the same scroll offset.
 */
@Composable
fun ResultsScreen(
    state: AppState,
    viewModel: PoraviaViewModel,
    onOpenJourney: (String) -> Unit,
    onOpenOperator: (String) -> Unit,
    onOpenStop: (String) -> Unit,
    onBack: () -> Unit,
    snackbarHost: SnackbarHostState,
) {
    val language = state.settings.resolvedLanguageTag
    val window = LocalPoraviaWindow.current
    val title = state.session.query.let { query ->
        if (query.origin != null && query.destination != null) {
            stringResource(
                R.string.results_for,
                query.origin?.label.orEmpty(),
                query.destination?.label.orEmpty(),
            )
        } else {
            stringResource(R.string.results_title)
        }
    }

    PoraviaScreen(title = title, onBack = onBack, constrainWidth = window.paneCount == 1) { padding ->
        if (window.paneCount == 2) {
            TwoPaneLayout(
                modifier = Modifier.fillMaxSize().padding(padding),
                fold = window.fold,
                listPane = { modifier ->
                    ResultsList(
                        state = state,
                        viewModel = viewModel,
                        languageTag = language,
                        onOpenJourney = onOpenJourney,
                        onOpenOperator = onOpenOperator,
                        modifier = modifier,
                    )
                },
                detailPane = { modifier ->
                    Box(modifier) {
                        val selected = state.session.selectedJourneyId
                        if (selected == null) {
                            OutcomeState(
                                title = stringResource(R.string.results_select_hint),
                                body = null,
                                isError = false,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            JourneyDetailPane(
                                state = state,
                                viewModel = viewModel,
                                journeyId = selected,
                                serviceDate = state.session.query.serviceDate,
                                onOpenOperator = onOpenOperator,
                                onOpenStop = onOpenStop,
                                snackbarHost = snackbarHost,
                            )
                        }
                    }
                },
            )
        } else {
            ResultsList(
                state = state,
                viewModel = viewModel,
                languageTag = language,
                onOpenJourney = onOpenJourney,
                onOpenOperator = onOpenOperator,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun ResultsList(
    state: AppState,
    viewModel: PoraviaViewModel,
    languageTag: String,
    onOpenJourney: (String) -> Unit,
    onOpenOperator: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = currentLocale()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.session.resultsScrollIndex,
        initialFirstVisibleItemScrollOffset = state.session.resultsScrollOffset,
    )

    // The scroll offset is part of the session, so it survives a rotation, a
    // fold and process death rather than snapping back to the top.
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .debounce(SCROLL_DEBOUNCE_MILLIS)
            .collect { (index, offset) -> viewModel.setResultsScroll(index, offset) }
    }

    LoadableContent(
        value = state.results,
        modifier = modifier,
        onRetry = { viewModel.runSearch(force = true) },
        idle = {
            OutcomeState(
                title = context.getString(R.string.search_needs_both),
                body = null,
                isError = false,
                modifier = modifier,
            )
        },
        emptyOverride = { reason -> EmptyResults(reason, state, viewModel, modifier) },
    ) { results ->
        LazyColumn(
            state = listState,
            modifier = modifier.testTag("results-list"),
            contentPadding = PaddingValues(Space.x4),
            verticalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            item(key = "header") {
                ResultsHeader(state, results, locale)
            }
            items(results.results, key = { it.id }) { journey ->
                JourneyCard(
                    journey = journey,
                    languageTag = languageTag,
                    selected = state.session.selectedJourneyId == journey.id,
                    onClick = { onOpenJourney(journey.id) },
                    onOperator = { onOpenOperator(journey.operator.id) },
                )
            }
            item(key = "live") {
                Spacer(Modifier.height(Space.x3))
                LiveUnavailableNotice()
            }
        }
    }
}

/**
 * The empty answer, and the one distinction the product exists to protect.
 *
 * A journeys pack is materialised only for the dates the published data names.
 * When the installed release holds no pack for the requested date, the honest
 * answer is "this device has no timetable for that date". It is not "nothing
 * runs that date", and saying so would invent a certainty the data does not
 * support. Which sentence appears is decided by the core's own
 * [dev.peterdsp.poravia.core.model.DateDataState], carried through the feature
 * layer as a distinct [EmptyReason], and never inferred here.
 */
@Composable
private fun EmptyResults(
    reason: EmptyReason,
    state: AppState,
    viewModel: PoraviaViewModel,
    modifier: Modifier,
) {
    val copy = emptyCopy(reason)
    val filtered = reason == EmptyReason.FILTERED_OUT
    OutcomeState(
        title = copy.title,
        body = copy.body,
        isError = false,
        modifier = modifier.testTag(
            if (reason == EmptyReason.NO_OFFLINE_DATA_FOR_DATE) {
                "results-no-offline-pack"
            } else {
                "results-empty"
            },
        ),
        onRetry = if (filtered) viewModel::clearFilters else null,
        retryLabel = stringResource(R.string.action_clear_filters),
    )
}

@Composable
private fun ResultsHeader(
    state: AppState,
    results: JourneyResults,
    locale: java.util.Locale,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        if (state.isDemo) {
            DemoNotice()
            Spacer(Modifier.height(Space.x3))
        }
        Text(
            text = Formats.serviceDateLong(results.query.date, locale),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (results.results.size) {
                    0 -> ""
                    1 -> context.getString(R.string.results_count_one)
                    else -> context.getString(R.string.results_count, results.results.size)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (results.servedFromCache) {
                    context.getString(R.string.results_cached_release, results.releaseId)
                } else {
                    context.getString(R.string.results_from_service)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        if (results.coverage == CoverageState.PARTIAL && results.results.isNotEmpty()) {
            Spacer(Modifier.height(Space.x3))
            InfoPanel(
                title = stringResource(R.string.results_partial_coverage_title),
                body = stringResource(R.string.results_partial_coverage_body),
                tone = Tone.WARNING,
                modifier = Modifier.testTag("results-partial"),
            )
        }
    }
}

private const val SCROLL_DEBOUNCE_MILLIS = 150L
