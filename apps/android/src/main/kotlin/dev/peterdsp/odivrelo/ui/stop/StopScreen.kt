package dev.peterdsp.odivrelo.ui.stop

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.peterdsp.odivrelo.R
import dev.peterdsp.odivrelo.core.model.DateDataState
import dev.peterdsp.odivrelo.state.AppState
import dev.peterdsp.odivrelo.state.OdivreloViewModel
import dev.peterdsp.odivrelo.theme.Space
import dev.peterdsp.odivrelo.ui.common.Badge
import dev.peterdsp.odivrelo.ui.common.CoverageBadge
import dev.peterdsp.odivrelo.ui.common.DemoNotice
import dev.peterdsp.odivrelo.ui.common.Formats
import dev.peterdsp.odivrelo.ui.common.KeyValueRow
import dev.peterdsp.odivrelo.ui.common.LoadableContent
import dev.peterdsp.odivrelo.ui.common.OutcomeState
import dev.peterdsp.odivrelo.ui.common.OutlinedBox
import dev.peterdsp.odivrelo.ui.common.RightsBadge
import dev.peterdsp.odivrelo.ui.common.SectionTitle
import dev.peterdsp.odivrelo.ui.common.StepFreeBadge
import dev.peterdsp.odivrelo.ui.common.OdivreloScreen
import dev.peterdsp.odivrelo.ui.common.Tone
import dev.peterdsp.odivrelo.ui.currentLocale
import dev.peterdsp.odivrelo.ui.results.JourneyCard

/**
 * A station or boarding point, and what leaves from it on a service date.
 *
 * Contract 1 carries a station's departures in the same shape as a search
 * result, so this page and the result list can never disagree about a
 * departure, and the same card renders both.
 */
@Composable
fun StopScreen(
    stopId: String,
    serviceDate: String,
    state: AppState,
    viewModel: OdivreloViewModel,
    onBack: () -> Unit,
    onOpenJourney: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val locale = currentLocale()
    val language = state.settings.resolvedLanguageTag

    LaunchedEffect(stopId, serviceDate) {
        if (stopId.isNotBlank()) viewModel.loadStop(stopId, serviceDate)
    }

    val isFavorite = state.favorites.valueOrNull?.any { it.placeId == stopId } == true

    OdivreloScreen(
        title = stringResource(R.string.stop_title),
        onBack = onBack,
        actions = {
            IconButton(
                onClick = { viewModel.toggleFavorite(stopId) },
                modifier = Modifier.testTag("stop-favorite"),
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.favorite_remove else R.string.favorite_add,
                    ),
                )
            }
        },
    ) { padding ->
        LoadableContent(
            value = state.stop,
            modifier = Modifier.fillMaxSize().padding(padding),
            onRetry = { viewModel.loadStop(stopId, serviceDate) },
        ) { detail ->
            val stop = detail.stop
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("stop-departures"),
                contentPadding = PaddingValues(Space.x4),
                verticalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                item(key = "header") {
                    Column {
                        if (state.isDemo) {
                            DemoNotice()
                            Spacer(Modifier.height(Space.x3))
                        }
                        Text(
                            text = stop.name.resolve(language),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.testTag("stop-name"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                            Badge(
                                text = stringResource(
                                    if (stop.kind ==
                                        dev.peterdsp.odivrelo.core.model.PlaceKind.STOP_PLACE
                                    ) {
                                        R.string.place_terminal
                                    } else {
                                        R.string.place_boarding_point
                                    },
                                ),
                                tone = Tone.NEUTRAL,
                            )
                            CoverageBadge(stop.coverage)
                        }
                        stop.parentName?.let {
                            Spacer(Modifier.height(Space.x2))
                            Text(
                                text = context.getString(
                                    R.string.stop_part_of,
                                    it.resolve(language),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        stop.municipality?.takeIf { it.isNotBlank() }?.let {
                            KeyValueRow(stringResource(R.string.booking_address), it)
                        }
                        stop.bay?.takeIf { it.isNotBlank() }?.let {
                            KeyValueRow(
                                stringResource(R.string.journey_boarding_point),
                                context.getString(R.string.journey_boarding_bay, it),
                            )
                        }
                        if (stop.latitude != null && stop.longitude != null) {
                            KeyValueRow(
                                label = stringResource(R.string.diagnostics_window),
                                value = context.getString(
                                    R.string.stop_coordinates,
                                    stop.latitude.toString(),
                                    stop.longitude.toString(),
                                ),
                            )
                        }
                        Spacer(Modifier.height(Space.x2))
                        Text(
                            text = Formats.serviceDateLong(detail.serviceDate, locale),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                if (stop.boardingPoints.isNotEmpty()) {
                    item(key = "boarding-title") {
                        SectionTitle(stringResource(R.string.stop_boarding_points_title))
                    }
                    items(stop.boardingPoints, key = { it.stopId }) { point ->
                        OutlinedBox {
                            Column {
                                Text(
                                    text = point.name.resolve(language),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(Space.x2))
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                                    point.bay?.let {
                                        Badge(
                                            context.getString(
                                                R.string.journey_boarding_bay,
                                                it,
                                            ),
                                            Tone.INFO,
                                        )
                                    }
                                    StepFreeBadge(point.stepFree)
                                }
                            }
                        }
                    }
                }

                item(key = "departures-title") {
                    SectionTitle(stringResource(R.string.stop_departures_title))
                }

                if (stop.departures.isEmpty()) {
                    item(key = "departures-empty") {
                        // The same distinction the search makes. No pack for the
                        // date is a statement about this device's data, not about
                        // whether a coach runs.
                        if (detail.dateDataState == DateDataState.NO_OFFLINE_PACK) {
                            OutcomeState(
                                title = context.getString(
                                    R.string.results_empty_no_offline_title,
                                ),
                                body = context.getString(
                                    R.string.results_empty_no_offline_body,
                                ),
                                isError = false,
                                modifier = Modifier.testTag("stop-no-offline-pack"),
                            )
                        } else {
                            OutcomeState(
                                title = context.getString(R.string.stop_no_departures),
                                body = context.getString(
                                    R.string.results_empty_no_service_body,
                                ),
                                isError = false,
                                modifier = Modifier.testTag("stop-no-departures"),
                            )
                        }
                    }
                } else {
                    items(stop.departures, key = { it.id }) { journey ->
                        JourneyCard(
                            journey = journey,
                            languageTag = language,
                            selected = false,
                            onClick = { onOpenJourney(journey.id, detail.serviceDate) },
                        )
                    }
                }

                if (stop.provenance.isNotEmpty()) {
                    item(key = "provenance-title") {
                        SectionTitle(stringResource(R.string.journey_provenance_title))
                    }
                    items(stop.provenance, key = { it.sourceId }) { provenance ->
                        OutlinedBox {
                            Column {
                                Text(
                                    text = provenance.sourceName,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(Space.x2))
                                RightsBadge(provenance.rightsStatus)
                            }
                        }
                    }
                }
            }
        }
    }
}
