package dev.peterdsp.odivrelo.ui.journey

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.peterdsp.odivrelo.R
import dev.peterdsp.odivrelo.core.model.BoardingRule
import dev.peterdsp.odivrelo.core.model.JourneyDetailBody
import dev.peterdsp.odivrelo.core.model.JourneyStop
import dev.peterdsp.odivrelo.core.model.SegmentRole
import dev.peterdsp.odivrelo.core.model.ReviewState
import dev.peterdsp.odivrelo.features.FailureReason
import dev.peterdsp.odivrelo.features.Loadable
import dev.peterdsp.odivrelo.state.AppState
import dev.peterdsp.odivrelo.state.OdivreloViewModel
import dev.peterdsp.odivrelo.theme.Space
import dev.peterdsp.odivrelo.ui.booking.Handoff
import dev.peterdsp.odivrelo.ui.booking.HandoffResult
import dev.peterdsp.odivrelo.ui.common.Badge
import dev.peterdsp.odivrelo.ui.common.ConfidenceBadge
import dev.peterdsp.odivrelo.ui.common.DemoNotice
import dev.peterdsp.odivrelo.ui.common.Formats
import dev.peterdsp.odivrelo.ui.common.FreshnessBadge
import dev.peterdsp.odivrelo.ui.common.GeometryBadge
import dev.peterdsp.odivrelo.ui.common.InfoPanel
import dev.peterdsp.odivrelo.ui.common.KeyValueRow
import dev.peterdsp.odivrelo.ui.common.LiveUnavailableNotice
import dev.peterdsp.odivrelo.ui.common.LoadableContent
import dev.peterdsp.odivrelo.ui.common.OutcomeState
import dev.peterdsp.odivrelo.ui.common.OutlinedBox
import dev.peterdsp.odivrelo.ui.common.OdivreloButton
import dev.peterdsp.odivrelo.ui.common.OdivreloScreen
import dev.peterdsp.odivrelo.ui.common.RightsBadge
import dev.peterdsp.odivrelo.ui.common.SectionTitle
import dev.peterdsp.odivrelo.ui.common.StepFreeBadge
import dev.peterdsp.odivrelo.ui.common.ThinDivider
import dev.peterdsp.odivrelo.ui.common.TimeQualityBadge
import dev.peterdsp.odivrelo.ui.common.Tone
import dev.peterdsp.odivrelo.ui.currentLocale
import kotlinx.coroutines.launch

/** The journey detail as a screen of its own, on a narrow window. */
@Composable
fun JourneyDetailScreen(
    state: AppState,
    viewModel: OdivreloViewModel,
    journeyId: String,
    serviceDate: String,
    onBack: () -> Unit,
    onOpenOperator: (String) -> Unit,
    onOpenStop: (String) -> Unit,
    snackbarHost: SnackbarHostState,
) {
    OdivreloScreen(
        title = stringResource(R.string.journey_title),
        onBack = onBack,
    ) { padding ->
        JourneyDetailPane(
            state = state,
            viewModel = viewModel,
            journeyId = journeyId,
            serviceDate = serviceDate,
            onOpenOperator = onOpenOperator,
            onOpenStop = onOpenStop,
            snackbarHost = snackbarHost,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

/**
 * Everything known about one journey, with the provenance of each claim.
 *
 * This is the screen a traveller reads at the bay, so it works from the saved
 * copy with no connection and says when it is doing that.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyDetailPane(
    state: AppState,
    viewModel: OdivreloViewModel,
    journeyId: String,
    serviceDate: String,
    onOpenOperator: (String) -> Unit,
    onOpenStop: (String) -> Unit,
    snackbarHost: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = currentLocale()
    val language = state.settings.resolvedLanguageTag

    LaunchedEffect(journeyId, serviceDate) {
        if (journeyId.isNotBlank()) viewModel.loadJourney(journeyId, serviceDate)
    }

    // The core says plainly when it holds no timetable for this date. That is a
    // statement about coverage, not about service, so it gets its own copy and
    // no retry button: retrying the same request cannot help.
    val failed = state.journey as? Loadable.Failed
    if (failed?.reason == FailureReason.NO_OFFLINE_DATA_FOR_DATE) {
        OutcomeState(
            title = context.getString(R.string.results_empty_no_offline_title),
            body = context.getString(R.string.results_empty_no_offline_body),
            isError = false,
            modifier = modifier.testTag("journey-no-offline-pack"),
        )
        return
    }

    LoadableContent(
        value = state.journey,
        modifier = modifier,
        onRetry = { viewModel.loadJourney(journeyId, serviceDate, force = true) },
    ) { view ->
        val journey = view.detail.journey
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x4),
        ) {
            if (state.isDemo) {
                Spacer(Modifier.height(Space.x2))
                DemoNotice()
            }

            if (view.fromSavedCopy) {
                Spacer(Modifier.height(Space.x3))
                InfoPanel(
                    title = context.getString(
                        R.string.journey_from_saved_copy,
                        view.detail.releaseId,
                    ),
                    body = context.getString(R.string.saved_trip_age_note),
                    tone = Tone.WARNING,
                    modifier = Modifier.testTag("journey-from-saved-copy"),
                )
            }

            Spacer(Modifier.height(Space.x4))
            Header(journey, language, locale, onOpenOperator)

            Spacer(Modifier.height(Space.x4))
            LiveUnavailableNotice()

            BoardingPointSection(journey, language, locale)

            StopsSection(
                journey = journey,
                language = language,
                alwaysExpanded = state.settings.alwaysExpandStops,
                onOpenStop = onOpenStop,
            )

            RestrictionsSection(journey, language)

            PurchaseSection(
                journey = journey,
                language = language,
                viewModel = viewModel,
                snackbarHost = snackbarHost,
            )

            SavedTripSection(state, viewModel, journeyId, serviceDate)

            ProvenanceSection(journey, locale)

            journey.correctionUrl?.let { url ->
                Spacer(Modifier.height(Space.x5))
                CorrectionLink(url)
            }

            Spacer(Modifier.height(Space.x10))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    journey: JourneyDetailBody,
    language: String,
    locale: java.util.Locale,
    onOpenOperator: (String) -> Unit,
) {
    val context = LocalContext.current
    val departure = Formats.time(context, journey.departure.at)
    val arrival = Formats.time(context, journey.arrival.at)

    KeyValueRow(
        label = stringResource(R.string.journey_operating_date),
        value = Formats.serviceDateLong(journey.serviceDate, locale),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clickable { onOpenOperator(journey.operator.id) }
            .testTag("journey-operator"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyValueRow(
            label = stringResource(R.string.journey_operator),
            value = journey.operator.name.resolve(language),
        )
    }

    ThinDivider()
    Spacer(Modifier.height(Space.x4))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(departure, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = journey.departure.stopName.resolve(language),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.x1))
            TimeQualityBadge(journey.departure.quality)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(arrival, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = journey.arrival.stopName.resolve(language),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.height(Space.x1))
            TimeQualityBadge(journey.arrival.quality)
        }
    }

    Spacer(Modifier.height(Space.x3))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Badge(Formats.duration(context, journey.durationMinutes), Tone.NEUTRAL)
        if (journey.crossesMidnight) {
            Badge(
                text = context.getString(R.string.crosses_midnight),
                tone = Tone.INFO,
                spoken = context.getString(
                    R.string.crosses_midnight_detail,
                    departure,
                    arrival,
                ),
                modifier = Modifier.testTag("journey-crosses-midnight"),
            )
        }
        FreshnessBadge(journey.freshness)
        ConfidenceBadge(journey.confidence)
        journey.fare?.let { fare ->
            Badge(
                text = context.getString(
                    R.string.fare_indicative,
                    Formats.money(fare.amount, fare.currency),
                ),
                tone = Tone.NEUTRAL,
                spoken = context.getString(
                    R.string.fare_indicative,
                    Formats.money(fare.amount, fare.currency),
                ) + ". " + context.getString(R.string.fare_indicative_help),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardingPointSection(
    journey: JourneyDetailBody,
    language: String,
    locale: java.util.Locale,
) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.journey_boarding_point))
    val point = journey.boardingPoint
    if (point == null) {
        Text(
            text = stringResource(R.string.journey_boarding_not_reviewed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    OutlinedBox(modifier = Modifier.testTag("journey-boarding-point")) {
        Column {
            Text(point.name.resolve(language), style = MaterialTheme.typography.titleMedium)
            point.terminalName?.let {
                Text(
                    text = context.getString(
                        R.string.place_inside_terminal,
                        it.resolve(language),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.x2))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
                verticalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                point.bay?.let {
                    Badge(context.getString(R.string.journey_boarding_bay, it), Tone.INFO)
                }
                StepFreeBadge(point.stepFree)
                if (point.reviewState.isShowable && point.reviewedAt != null) {
                    Badge(
                        text = context.getString(
                            R.string.journey_boarding_reviewed,
                            Formats.timestamp(point.reviewedAt, locale).orEmpty(),
                        ),
                        tone = Tone.SUCCESS,
                    )
                } else if (point.reviewState == ReviewState.CANDIDATE) {
                    Badge(
                        text = context.getString(R.string.journey_boarding_not_reviewed),
                        tone = Tone.WARNING,
                    )
                }
            }
            point.instructions?.resolve(language)?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Space.x2))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    MapOrList(journey, language, point.latitude, point.longitude)
}

/**
 * Map and list are alternatives, and the list is complete on its own.
 *
 * There are no offline map tiles in this release, so Odivrelo does not draw a
 * map. It says so, and it hands the coordinate to the device's own maps
 * application. Everything a traveller needs is reachable without ever opening
 * one, which is the non-map route the accessibility gate requires.
 */
@Composable
private fun MapOrList(
    journey: JourneyDetailBody,
    language: String,
    latitude: Double?,
    longitude: Double?,
) {
    val context = LocalContext.current
    var showMap by rememberSaveable(journey.id) { mutableStateOf(false) }
    var mapMessage by rememberSaveable(journey.id) { mutableStateOf<String?>(null) }

    Spacer(Modifier.height(Space.x4))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !showMap,
            onClick = { showMap = false },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag("journey-view-list"),
        ) {
            Text(stringResource(R.string.journey_view_list))
        }
        SegmentedButton(
            selected = showMap,
            onClick = { showMap = true },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag("journey-view-map"),
        ) {
            Text(stringResource(R.string.journey_view_map))
        }
    }

    if (showMap) {
        Spacer(Modifier.height(Space.x3))
        // The embedded OpenFreeMap map, with the route and the stops resolved by
        // their own coordinates. The stop list below stays the complete,
        // accessible alternative.
        CoachMap(
            stops = journey.stops,
            geometry = journey.geometry,
            boardingStopId = journey.selectedSegment?.boardStopId ?: journey.boardingPoint?.stopId,
            alightStopId = journey.selectedSegment?.alightStopId,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(Space.x3))
                .testTag("journey-map"),
        )
        Spacer(Modifier.height(Space.x3))
        InfoPanel(
            title = stringResource(R.string.journey_map_note),
            body = mapMessage,
            tone = Tone.INFO,
        ) {
            OdivreloButton(
                text = stringResource(R.string.journey_open_in_maps),
                onClick = {
                    if (latitude != null && longitude != null) {
                        val outcome = Handoff.openMap(
                            context = context,
                            latitude = latitude,
                            longitude = longitude,
                            label = journey.departure.stopName.resolve(language),
                        )
                        mapMessage = if (outcome == HandoffResult.OPENED) {
                            null
                        } else {
                            context.getString(R.string.journey_no_map_app)
                        }
                    } else {
                        mapMessage = context.getString(R.string.geometry_none)
                    }
                },
                outlined = true,
                modifier = Modifier.testTag("journey-open-map"),
            )
        }
        journey.geometry?.let { geometry ->
            Spacer(Modifier.height(Space.x3))
            GeometryBadge(geometry.confidence)
            geometry.attribution?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StopsSection(
    journey: JourneyDetailBody,
    language: String,
    alwaysExpanded: Boolean,
    onOpenStop: (String) -> Unit,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(journey.id) { mutableStateOf(alwaysExpanded) }
    val stops = journey.stops

    SectionTitle(
        context.getString(R.string.journey_stops_title) + " · " +
            context.getString(R.string.journey_stops_count, stops.size),
    )

    if (stops.isEmpty()) {
        Text(
            text = stringResource(R.string.state_no_data_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val shown = if (expanded || alwaysExpanded) stops else stops.take(COLLAPSED_STOPS)
    shown.forEachIndexed { index, stop ->
        StopRow(
            stop = stop,
            language = language,
            index = index,
            total = stops.size,
            onClick = { onOpenStop(stop.stopId) },
        )
    }
    if (!alwaysExpanded && stops.size > COLLAPSED_STOPS) {
        OdivreloButton(
            text = stringResource(
                if (expanded) R.string.action_show_less else R.string.action_show_more,
            ),
            onClick = { expanded = !expanded },
            outlined = true,
            modifier = Modifier.fillMaxWidth().testTag("journey-toggle-stops"),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopRow(
    stop: JourneyStop,
    language: String,
    index: Int,
    total: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val name = stop.name.resolve(language)
    val departure = stop.departureAt?.let { Formats.time(context, it) }
    val arrival = stop.arrivalAt?.let { Formats.time(context, it) }
    val pickup = context.getString(
        when (stop.pickup) {
            BoardingRule.ALLOWED -> R.string.pickup_allowed
            BoardingRule.NOT_ALLOWED -> R.string.pickup_not_allowed
            BoardingRule.ON_REQUEST -> R.string.pickup_on_request
            BoardingRule.COORDINATE_WITH_OPERATOR -> R.string.pickup_coordinate
        },
    )
    val dropoff = context.getString(
        when (stop.dropoff) {
            BoardingRule.ALLOWED -> R.string.dropoff_allowed
            BoardingRule.NOT_ALLOWED -> R.string.dropoff_not_allowed
            BoardingRule.ON_REQUEST -> R.string.dropoff_on_request
            BoardingRule.COORDINATE_WITH_OPERATOR -> R.string.dropoff_coordinate
        },
    )
    val segmentLabel = when (stop.segmentRole) {
        SegmentRole.BOARD -> context.getString(R.string.journey_segment_board)
        SegmentRole.ALIGHT -> context.getString(R.string.journey_segment_alight)
        SegmentRole.BEFORE_BOARD, SegmentRole.AFTER_ALIGHT ->
            context.getString(R.string.journey_segment_outside)
        SegmentRole.ON_SEGMENT -> null
    }
    val outside = stop.segmentRole == SegmentRole.BEFORE_BOARD || stop.segmentRole == SegmentRole.AFTER_ALIGHT

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = Space.x2)
            .alpha(if (outside) 0.6f else 1f)
            .testTag("journey-stop-" + stop.stopId)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    context.getString(R.string.a11y_stop_sequence, index + 1, total),
                    name,
                    segmentLabel,
                    arrival?.let { context.getString(R.string.journey_view_list) + " " + it },
                    departure,
                    pickup,
                    dropoff,
                ).joinToString(", ")
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = departure ?: arrival ?: context.getString(R.string.quality_unknown),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.height(Space.x1))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalArrangement = Arrangement.spacedBy(Space.x1),
        ) {
            when (stop.segmentRole) {
                SegmentRole.BOARD -> segmentLabel?.let { Badge(it, Tone.WARNING) }
                SegmentRole.ALIGHT -> segmentLabel?.let { Badge(it, Tone.INFO) }
                SegmentRole.BEFORE_BOARD, SegmentRole.AFTER_ALIGHT -> segmentLabel?.let { Badge(it, Tone.NEUTRAL) }
                SegmentRole.ON_SEGMENT -> Unit
            }
            TimeQualityBadge(stop.timeQuality)
            Badge(pickup, if (stop.pickup == BoardingRule.ALLOWED) Tone.NEUTRAL else Tone.WARNING)
            Badge(dropoff, if (stop.dropoff == BoardingRule.ALLOWED) Tone.NEUTRAL else Tone.WARNING)
        }
    }
    ThinDivider()
}

@Composable
private fun RestrictionsSection(journey: JourneyDetailBody, language: String) {
    SectionTitle(stringResource(R.string.journey_restrictions_title))
    if (journey.restrictions.isEmpty()) {
        Text(
            text = stringResource(R.string.journey_restrictions_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    journey.restrictions.forEach { restriction ->
        InfoPanel(
            title = restriction.text.resolve(language),
            body = null,
            tone = Tone.WARNING,
            modifier = Modifier.padding(vertical = Space.x1),
        )
    }
}

@Composable
private fun ProvenanceSection(journey: JourneyDetailBody, locale: java.util.Locale) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.journey_provenance_title))
    if (journey.provenance.isEmpty()) {
        Text(
            text = stringResource(R.string.rights_unknown_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    journey.provenance.forEach { provenance ->
        OutlinedBox(modifier = Modifier.padding(vertical = Space.x1)) {
            Column {
                Text(provenance.sourceName, style = MaterialTheme.typography.titleSmall)
                provenance.sourceUrl?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Space.x2))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    RightsBadge(provenance.rightsStatus)
                    provenance.licence?.let {
                        Badge(context.getString(R.string.sources_licence, it), Tone.NEUTRAL)
                    }
                }
                Formats.timestamp(provenance.retrievedAt, locale)?.let {
                    Spacer(Modifier.height(Space.x1))
                    Text(
                        text = context.getString(R.string.journey_provenance_retrieved, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CorrectionLink(url: String) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    var message by rememberSaveable(url) { mutableStateOf<String?>(null) }
    OdivreloButton(
        text = stringResource(R.string.journey_correction),
        onClick = {
            val outcome = Handoff.openUrl(context, url, primary, surface)
            message = if (outcome == HandoffResult.OPENED) {
                null
            } else {
                context.getString(R.string.booking_no_browser)
            }
        },
        outlined = true,
        modifier = Modifier.fillMaxWidth().testTag("journey-correction"),
    )
    message?.let {
        Spacer(Modifier.height(Space.x2))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

private const val COLLAPSED_STOPS = 4
