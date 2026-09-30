package dev.peterdsp.odivrelo.ui.results

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.peterdsp.odivrelo.R
import dev.peterdsp.odivrelo.core.model.Journey
import dev.peterdsp.odivrelo.theme.Space
import dev.peterdsp.odivrelo.ui.common.Badge
import dev.peterdsp.odivrelo.ui.common.ConfidenceBadge
import dev.peterdsp.odivrelo.ui.common.Formats
import dev.peterdsp.odivrelo.ui.common.FreshnessBadge
import dev.peterdsp.odivrelo.ui.common.OdivreloCard
import dev.peterdsp.odivrelo.ui.common.StepFreeBadge
import dev.peterdsp.odivrelo.ui.common.TimeQualityBadge
import dev.peterdsp.odivrelo.ui.common.Tone

/**
 * One journey, with every claim labelled.
 *
 * Nothing on this card is presented as more certain than the data behind it:
 * the departure and arrival each carry their own time quality, the fare says it
 * is indicative, the freshness says when the fact was last checked and how old
 * that is, and the confidence says whether a person has reviewed it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyCard(
    journey: Journey,
    languageTag: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOperator: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val departure = Formats.time(context, journey.departure.at)
    val arrival = Formats.time(context, journey.arrival.at)
    val duration = Formats.duration(context, journey.durationMinutes)
    val operatorName = journey.operator.name.resolve(languageTag)

    val spoken = buildString {
        append(
            context.getString(
                R.string.a11y_journey_summary,
                operatorName,
                departure,
                journey.departure.stopName.resolve(languageTag),
                arrival,
                journey.arrival.stopName.resolve(languageTag),
                Formats.durationSpoken(context, journey.durationMinutes),
            ),
        )
        if (journey.crossesMidnight) {
            append(". ")
            append(context.getString(R.string.crosses_midnight))
        }
        append(". ")
        append(Formats.freshnessLabel(context, journey.freshness))
        append(", ")
        append(Formats.freshnessAge(context, journey.freshness))
    }

    OdivreloCard(
        modifier = modifier
            .clickable(onClick = onClick)
            .heightIn(min = Space.touchTarget)
            .testTag("journey-" + journey.id)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                this.selected = selected
            },
        selected = selected,
    ) {
        // Three columns that share the width by weight rather than by their own
        // appetite. A terminal name is long in every language this product
        // ships in, and letting it take what it wants is what turns an arrival
        // time into one character per line on a phone.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = departure, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = journey.departure.stopName.resolve(languageTag),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.x1))
                TimeQualityBadge(journey.departure.quality)
            }
            Column(
                modifier = Modifier.weight(0.9f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = duration,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    text = if (journey.intermediateStopCount == 0) {
                        context.getString(R.string.results_intermediate_none)
                    } else if (journey.intermediateStopCount == 1) {
                        context.getString(R.string.results_intermediate_one)
                    } else {
                        context.getString(
                            R.string.results_intermediate,
                            journey.intermediateStopCount,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(text = arrival, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = journey.arrival.stopName.resolve(languageTag),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
                Spacer(Modifier.height(Space.x1))
                TimeQualityBadge(journey.arrival.quality)
            }
        }

        Spacer(Modifier.height(Space.x3))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onOperator != null) {
                        Modifier.clickable(onClick = onOperator)
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = operatorName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            val fare = journey.fare
            Text(
                text = if (fare != null) {
                    context.getString(
                        R.string.fare_indicative,
                        Formats.money(fare.amount, fare.currency),
                    )
                } else {
                    context.getString(R.string.fare_unknown)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Space.x3))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            if (journey.crossesMidnight) {
                Badge(
                    text = context.getString(R.string.crosses_midnight),
                    tone = Tone.INFO,
                    spoken = context.getString(
                        R.string.crosses_midnight_detail,
                        departure,
                        arrival,
                    ),
                )
            }
            FreshnessBadge(journey.freshness)
            ConfidenceBadge(journey.confidence)
            StepFreeBadge(journey.accessibleBoardingPoint)
        }
    }
}
