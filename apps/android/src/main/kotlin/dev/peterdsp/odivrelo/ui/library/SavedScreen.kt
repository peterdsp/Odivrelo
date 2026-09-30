package dev.peterdsp.odivrelo.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.peterdsp.odivrelo.R
import dev.peterdsp.odivrelo.core.model.SavedTrip
import dev.peterdsp.odivrelo.state.AppState
import dev.peterdsp.odivrelo.state.OdivreloViewModel
import dev.peterdsp.odivrelo.theme.Space
import dev.peterdsp.odivrelo.ui.common.Badge
import dev.peterdsp.odivrelo.ui.common.DemoNotice
import dev.peterdsp.odivrelo.ui.common.Formats
import dev.peterdsp.odivrelo.ui.common.InfoPanel
import dev.peterdsp.odivrelo.ui.common.OutcomeState
import dev.peterdsp.odivrelo.ui.common.OdivreloCard
import dev.peterdsp.odivrelo.ui.common.OdivreloScreen
import dev.peterdsp.odivrelo.ui.common.SectionTitle
import dev.peterdsp.odivrelo.ui.common.StepFreeBadge
import dev.peterdsp.odivrelo.ui.common.Tone
import dev.peterdsp.odivrelo.ui.currentLocale

/**
 * Favourites and saved trips: the offline half of the product.
 *
 * Everything here is readable with no connection, and every card says which
 * cached release it came from and how old that copy is, because a saved trip is
 * a photograph of a timetable and not a live one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedScreen(
    state: AppState,
    viewModel: OdivreloViewModel,
    onOpenJourney: (String, String) -> Unit,
    snackbarHost: SnackbarHostState,
) {
    val context = LocalContext.current
    val locale = currentLocale()
    val language = state.settings.resolvedLanguageTag

    LaunchedEffect(Unit) { viewModel.refreshLibrary() }

    OdivreloScreen(title = stringResource(R.string.nav_saved)) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("saved-list"),
            contentPadding = PaddingValues(Space.x4),
            verticalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            if (state.isDemo) {
                item(key = "demo") { DemoNotice() }
            }

            item(key = "trip-ready") {
                InfoPanel(
                    title = stringResource(R.string.trip_ready_title),
                    body = stringResource(R.string.trip_ready_body) + " " +
                        stringResource(R.string.trip_ready_offline),
                    tone = Tone.INFO,
                    modifier = Modifier.testTag("trip-ready"),
                )
            }

            item(key = "trips-title") {
                SectionTitle(stringResource(R.string.saved_trips_title))
            }

            val trips = state.savedTrips.valueOrNull.orEmpty()
            if (trips.isEmpty()) {
                item(key = "trips-empty") {
                    OutcomeState(
                        title = context.getString(R.string.saved_trips_empty_title),
                        body = context.getString(R.string.saved_trips_empty_body),
                        isError = false,
                        modifier = Modifier.testTag("saved-trips-empty"),
                    )
                }
            } else {
                items(trips, key = { it.id }) { trip ->
                    SavedTripCard(
                        trip = trip,
                        languageTag = language,
                        locale = locale,
                        hasReminder = state.reminders.any { it.savedTripId == trip.id },
                        onOpen = { onOpenJourney(trip.journeyId, trip.serviceDate) },
                        onRemove = { viewModel.removeSavedTrip(trip.id) },
                    )
                }
            }

            item(key = "favorites-title") {
                SectionTitle(stringResource(R.string.favorites_title))
            }

            val favorites = state.favorites.valueOrNull.orEmpty()
            if (favorites.isEmpty()) {
                item(key = "favorites-empty") {
                    OutcomeState(
                        title = context.getString(R.string.favorites_empty_title),
                        body = context.getString(R.string.favorites_empty_body),
                        isError = false,
                        modifier = Modifier.testTag("favorites-empty"),
                    )
                }
            } else {
                items(favorites, key = { it.placeId }) { favorite ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Space.touchTarget)
                            .testTag("favorite-" + favorite.placeId),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = favorite.name.resolve(language),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            favorite.municipality?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.toggleFavorite(favorite.placeId) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.favorite_remove),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedTripCard(
    trip: SavedTrip,
    languageTag: String,
    locale: java.util.Locale,
    hasReminder: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val departure = Formats.time(context, trip.departureAt)
    val arrival = Formats.time(context, trip.arrivalAt)
    val label = trip.originName.resolve(languageTag) + " → " +
        trip.destinationName.resolve(languageTag)

    OdivreloCard(
        modifier = Modifier
            .clickable(onClick = onOpen)
            .testTag("saved-trip-" + trip.id)
            .semantics(mergeDescendants = true) {
                contentDescription = label + ", " +
                    Formats.serviceDate(trip.serviceDate, locale) + ", " +
                    departure + " – " + arrival
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = Formats.serviceDateLong(trip.serviceDate, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = departure + " – " + arrival,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = trip.operatorName.resolve(languageTag),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.testTag("saved-trip-remove")) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.journey_unsave),
                )
            }
        }

        Spacer(Modifier.height(Space.x2))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            trip.boardingBay?.let {
                Badge(context.getString(R.string.journey_boarding_bay, it), Tone.INFO)
            }
            StepFreeBadge(trip.boardingStepFree)
            if (trip.crossesMidnight) {
                Badge(context.getString(R.string.crosses_midnight), Tone.INFO)
            }
            if (hasReminder) {
                Badge(context.getString(R.string.reminders_title), Tone.SUCCESS)
            }
            Badge(context.getString(R.string.trip_ready_offline), Tone.SUCCESS)
        }

        Spacer(Modifier.height(Space.x2))
        Text(
            text = context.getString(
                R.string.saved_trip_cached,
                Formats.timestamp(trip.cachedAt, locale).orEmpty(),
                trip.releaseId,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.saved_trip_age_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
