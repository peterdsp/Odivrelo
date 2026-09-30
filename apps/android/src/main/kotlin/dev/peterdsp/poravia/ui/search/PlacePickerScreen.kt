package dev.peterdsp.poravia.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.remember
import dev.peterdsp.poravia.R
import dev.peterdsp.poravia.core.model.Place
import dev.peterdsp.poravia.features.EmptyReason
import dev.peterdsp.poravia.features.search.PlaceGroup
import dev.peterdsp.poravia.features.search.SearchEndpoint
import dev.peterdsp.poravia.state.AppState
import dev.peterdsp.poravia.state.PoraviaViewModel
import dev.peterdsp.poravia.theme.Space
import dev.peterdsp.poravia.ui.common.Badge
import dev.peterdsp.poravia.ui.common.CoverageBadge
import dev.peterdsp.poravia.ui.common.LoadableContent
import dev.peterdsp.poravia.ui.common.OutcomeState
import dev.peterdsp.poravia.ui.common.PoraviaScreen
import dev.peterdsp.poravia.ui.common.Tone

/**
 * Choosing a place, with the one distinction that matters at a terminal.
 *
 * A terminal and a boarding point inside it are different answers to "where do
 * I get on", and a traveller standing at Kithra with three bays needs to see
 * which one they are choosing. Boarding points are therefore listed under their
 * terminal, with the terminal itself offered as a separate choice, rather than
 * flattened into one list of similar-looking names.
 */
@Composable
fun PlacePickerScreen(
    state: AppState,
    viewModel: PoraviaViewModel,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val language = state.settings.resolvedLanguageTag
    val focus = remember { FocusRequester() }
    val endpoint = state.session.pickingEndpoint ?: SearchEndpoint.ORIGIN

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    PoraviaScreen(
        title = stringResource(
            if (endpoint == SearchEndpoint.ORIGIN) {
                R.string.place_picker_origin_title
            } else {
                R.string.place_picker_destination_title
            },
        ),
        onBack = {
            viewModel.closePlacePicker()
            onDone()
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.placeQuery,
                onValueChange = viewModel::setPlaceQuery,
                label = { Text(stringResource(R.string.place_picker_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.x4)
                    .focusRequester(focus)
                    .testTag("place-query"),
            )

            LoadableContent(
                value = state.places,
                needsInputText = context.getString(R.string.place_picker_needs_input),
                onRetry = { viewModel.setPlaceQuery(state.placeQuery) },
                idle = {
                    OutcomeState(
                        title = context.getString(R.string.place_picker_needs_input),
                        body = null,
                        isError = false,
                    )
                },
                emptyOverride = { reason ->
                    OutcomeState(
                        title = when (reason) {
                            EmptyReason.NEEDS_INPUT ->
                                context.getString(R.string.place_picker_needs_input)

                            else -> context.getString(R.string.place_no_match)
                        },
                        body = null,
                        isError = false,
                    )
                },
            ) { groups ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("place-results"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Space.x4,
                        vertical = Space.x3,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    items(groups, key = { group -> group.key() }) { group ->
                        PlaceGroupRow(
                            group = group,
                            languageTag = language,
                            onChoose = { place, parent ->
                                viewModel.choosePlace(place, parent)
                                onDone()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun PlaceGroup.key(): String =
    (terminal?.id ?: "") + "|" + boardingPoints.joinToString(",") { it.id }

@Composable
private fun PlaceGroupRow(
    group: PlaceGroup,
    languageTag: String,
    onChoose: (Place, Place?) -> Unit,
) {
    val terminal = group.terminal
    Column(Modifier.fillMaxWidth()) {
        if (terminal != null) {
            PlaceRow(
                place = terminal,
                languageTag = languageTag,
                kindLabel = stringResource(R.string.place_terminal),
                subtitle = if (terminal.boardingPointCount > 0) {
                    stringResource(R.string.place_bay_count, terminal.boardingPointCount)
                } else {
                    null
                },
                onClick = { onChoose(terminal, null) },
            )
            if (group.hasDisambiguation) {
                Text(
                    text = stringResource(R.string.place_terminal_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.x4, bottom = Space.x1),
                )
            }
        }
        group.boardingPoints.forEach { point ->
            PlaceRow(
                place = point,
                languageTag = languageTag,
                kindLabel = stringResource(R.string.place_boarding_point),
                subtitle = terminal?.let {
                    stringResource(R.string.place_inside_terminal, it.name.resolve(languageTag))
                },
                indented = terminal != null,
                onClick = { onChoose(point, terminal) },
            )
        }
    }
}

@Composable
private fun PlaceRow(
    place: Place,
    languageTag: String,
    kindLabel: String,
    subtitle: String?,
    indented: Boolean = false,
    onClick: () -> Unit,
) {
    val name = place.name.resolve(languageTag)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clickable(onClick = onClick)
            .padding(
                start = if (indented) Space.x6 else Space.x2,
                end = Space.x2,
                top = Space.x2,
                bottom = Space.x2,
            )
            .testTag("place-" + place.id)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(name, kindLabel, subtitle).joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Badge(text = kindLabel, tone = Tone.NEUTRAL)
                CoverageBadge(place.coverage)
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(Space.x1))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!place.municipality.isNullOrBlank()) {
                Text(
                    text = place.municipality.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
