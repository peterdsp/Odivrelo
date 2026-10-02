package dev.peterdsp.odivrelo.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.peterdsp.odivrelo.R
import dev.peterdsp.odivrelo.core.model.Confidence
import dev.peterdsp.odivrelo.core.model.CoverageState
import dev.peterdsp.odivrelo.core.model.Freshness
import dev.peterdsp.odivrelo.core.model.FreshnessState
import dev.peterdsp.odivrelo.core.model.GeometryConfidence
import dev.peterdsp.odivrelo.core.model.RightsStatus
import dev.peterdsp.odivrelo.core.model.TimeQuality
import dev.peterdsp.odivrelo.features.EmptyReason
import dev.peterdsp.odivrelo.features.FailureReason
import dev.peterdsp.odivrelo.features.Loadable
import dev.peterdsp.odivrelo.theme.LocalExtraTouchPadding
import dev.peterdsp.odivrelo.theme.LocalReduceMotion
import dev.peterdsp.odivrelo.theme.OdivreloTheme
import dev.peterdsp.odivrelo.theme.Space

/** The tone of a status badge. Each one means something specific. */
enum class Tone { NEUTRAL, INFO, SUCCESS, WARNING, ERROR }

@Composable
private fun toneColors(tone: Tone): Pair<Color, Color> {
    val status = OdivreloTheme.status
    return when (tone) {
        Tone.NEUTRAL -> status.surfaceMuted to MaterialTheme.colorScheme.onSurfaceVariant
        Tone.INFO -> status.infoSurface to status.infoText
        Tone.SUCCESS -> status.successSurface to status.successText
        Tone.WARNING -> status.warningSurface to status.warningText
        Tone.ERROR -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
    }
}

/**
 * A small labelled fact.
 *
 * [spoken] exists because a badge that reads "Stale" out of context tells a
 * screen-reader user nothing; the spoken form says what is stale and why it
 * matters.
 */
@Composable
fun Badge(
    text: String,
    tone: Tone = Tone.NEUTRAL,
    spoken: String? = null,
    modifier: Modifier = Modifier,
) {
    val (background, foreground) = toneColors(tone)
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = Space.x3, vertical = Space.x1)
            .then(
                if (spoken != null) {
                    Modifier.clearAndSetSemantics { contentDescription = spoken }
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
        )
    }
}

@Composable
fun FreshnessBadge(freshness: Freshness, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val label = Formats.freshnessLabel(context, freshness)
    val age = Formats.freshnessAge(context, freshness)
    Badge(
        text = label + " · " + age,
        tone = when (freshness.state) {
            FreshnessState.FRESH -> Tone.SUCCESS
            FreshnessState.AGING -> Tone.WARNING
            FreshnessState.STALE -> Tone.ERROR
        },
        spoken = label + ". " + age,
        modifier = modifier,
    )
}

@Composable
fun TimeQualityBadge(quality: TimeQuality, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (label, help) = when (quality) {
        TimeQuality.SCHEDULED ->
            context.getString(R.string.quality_scheduled) to
                context.getString(R.string.quality_scheduled_help)

        TimeQuality.APPROXIMATE ->
            context.getString(R.string.quality_approximate) to
                context.getString(R.string.quality_approximate_help)

        TimeQuality.UNKNOWN ->
            context.getString(R.string.quality_unknown) to
                context.getString(R.string.quality_unknown)
    }
    Badge(
        text = label,
        tone = if (quality == TimeQuality.SCHEDULED) Tone.NEUTRAL else Tone.WARNING,
        spoken = label + ". " + help,
        modifier = modifier,
    )
}

@Composable
fun ConfidenceBadge(confidence: Confidence, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    when (confidence) {
        Confidence.REVIEWED -> Badge(
            text = context.getString(R.string.confidence_reviewed),
            tone = Tone.SUCCESS,
            modifier = modifier,
        )

        Confidence.CANDIDATE -> Badge(
            text = context.getString(R.string.confidence_candidate),
            tone = Tone.WARNING,
            spoken = context.getString(R.string.confidence_candidate) + ". " +
                context.getString(R.string.confidence_candidate_help),
            modifier = modifier,
        )
    }
}

@Composable
fun CoverageBadge(state: CoverageState, modifier: Modifier = Modifier) {
    val (label, tone) = when (state) {
        CoverageState.COVERED -> R.string.coverage_covered to Tone.SUCCESS
        CoverageState.PARTIAL -> R.string.coverage_partial to Tone.WARNING
        CoverageState.NOT_COVERED -> R.string.coverage_not_covered to Tone.ERROR
        CoverageState.DEMO -> R.string.coverage_demo to Tone.INFO
    }
    Badge(text = stringResource(label), tone = tone, modifier = modifier)
}

@Composable
fun StepFreeBadge(stepFree: Boolean?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    when (stepFree) {
        true -> Badge(context.getString(R.string.step_free_yes), Tone.SUCCESS, modifier = modifier)
        false -> Badge(context.getString(R.string.step_free_no), Tone.WARNING, modifier = modifier)
        null -> Badge(
            text = context.getString(R.string.step_free_unknown),
            tone = Tone.NEUTRAL,
            spoken = context.getString(R.string.step_free_unknown) + ". " +
                context.getString(R.string.step_free_unknown_help),
            modifier = modifier,
        )
    }
}

@Composable
fun RightsBadge(status: RightsStatus, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (label, tone) = when (status) {
        RightsStatus.ALLOWED -> R.string.rights_allowed to Tone.SUCCESS
        RightsStatus.PERMISSION_PENDING -> R.string.rights_permission_pending to Tone.WARNING
        RightsStatus.PROHIBITED -> R.string.rights_prohibited to Tone.ERROR
        RightsStatus.UNKNOWN -> R.string.rights_unknown to Tone.WARNING
    }
    Badge(
        text = context.getString(label),
        tone = tone,
        spoken = if (status == RightsStatus.UNKNOWN) {
            context.getString(label) + ". " + context.getString(R.string.rights_unknown_help)
        } else {
            null
        },
        modifier = modifier,
    )
}

@Composable
fun GeometryBadge(confidence: GeometryConfidence, modifier: Modifier = Modifier) {
    val label = when (confidence) {
        GeometryConfidence.UNVERIFIED -> R.string.geometry_unverified
        GeometryConfidence.ORDERED_STOPS_ONLY -> R.string.geometry_ordered_stops_only
        GeometryConfidence.OSM_CANDIDATE -> R.string.geometry_osm_candidate
        GeometryConfidence.REVIEWED -> R.string.geometry_reviewed
        GeometryConfidence.REJECTED -> R.string.geometry_none
    }
    Badge(
        text = stringResource(label),
        tone = if (confidence == GeometryConfidence.REVIEWED) Tone.SUCCESS else Tone.WARNING,
        modifier = modifier,
    )
}

/**
 * The demonstration notice.
 *
 * It has no close button and no state, by design: contract 1 requires a
 * persistent, non-dismissible statement while the release is in demonstration
 * mode, because a person who dismissed it once and came back a week later would
 * otherwise be reading invented departures as if they were real.
 */
@Composable
fun DemoNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val status = OdivreloTheme.status
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = context.getString(R.string.demo_notice_a11y)
            },
        color = status.warningSurface,
        contentColor = status.warningText,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(Space.x4),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Space.x3))
            Column {
                Text(
                    text = stringResource(R.string.demo_notice_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Space.x1))
                Text(
                    text = stringResource(R.string.demo_notice_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** The sentence that has to appear on every surface where money changes hands. */
@Composable
fun NoTicketsNotice(modifier: Modifier = Modifier, short: Boolean = false) {
    Text(
        text = stringResource(
            if (short) R.string.no_tickets_sold_short else R.string.no_tickets_sold,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Live tracking does not exist in this release, and the product says so. */
@Composable
fun LiveUnavailableNotice(modifier: Modifier = Modifier) {
    InfoPanel(
        title = stringResource(R.string.live_unavailable_title),
        body = stringResource(R.string.live_unavailable_body),
        tone = Tone.INFO,
        modifier = modifier,
    )
}

@Composable
fun InfoPanel(
    title: String,
    body: String?,
    tone: Tone = Tone.INFO,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val (background, foreground) = toneColors(tone)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = background,
        contentColor = foreground,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(Space.x4)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (!body.isNullOrBlank()) {
                Spacer(Modifier.height(Space.x1))
                Text(text = body, style = MaterialTheme.typography.bodyMedium)
            }
            if (action != null) {
                Spacer(Modifier.height(Space.x3))
                action()
            }
        }
    }
}

/**
 * What is shown in place of material nobody has the right to republish.
 *
 * An unknown rights status means "not allowed", not "allowed until someone
 * objects", so it is treated exactly like a prohibition here.
 */
@Composable
fun RestrictedPanel(modifier: Modifier = Modifier) {
    InfoPanel(
        title = stringResource(R.string.state_restricted_title),
        body = stringResource(R.string.state_restricted_body),
        tone = Tone.WARNING,
        modifier = modifier.testTag("restricted"),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Space.x5, bottom = Space.x2)
            .semantics { heading() },
    )
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.x2)
            .semantics(mergeDescendants = true) { contentDescription = label + ": " + value },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.x4))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun OdivreloCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(Modifier.padding(Space.x4 + LocalExtraTouchPadding.current)) { content() }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Space.x8)
            .semantics(mergeDescendants = true) {
                contentDescription = context.getString(R.string.state_loading_a11y)
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Under reduced motion, show a static ring rather than the spinning
        // indeterminate one. This honours the platform and in-app reduced-motion
        // setting (the same signal the rest of the theme already respects), and
        // because a static indicator requests no further frames it also lets the
        // Compose UI-test clock reach idle instead of waiting on a perpetual
        // animation. The loading text below carries the state to a screen reader.
        if (LocalReduceMotion.current) {
            CircularProgressIndicator(progress = { 0.25f })
        } else {
            CircularProgressIndicator()
        }
        Spacer(Modifier.height(Space.x4))
        Text(
            text = stringResource(R.string.state_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Title and body for an empty outcome, so every caller says the same thing. */
data class EmptyCopy(val title: String, val body: String?)

@Composable
fun emptyCopy(reason: EmptyReason, needsInputText: String? = null): EmptyCopy =
    emptyCopyOf(LocalContext.current, reason, needsInputText)

/**
 * The reason-to-sentence mapping, outside composition so a test can assert it
 * directly. The distinction between "no offline data for this date" and "no
 * service on this date" is the one this product exists to protect, and a test
 * that has to spin up a composition to check it is a test nobody runs.
 */
fun emptyCopyOf(
    context: android.content.Context,
    reason: EmptyReason,
    needsInputText: String? = null,
): EmptyCopy {
    return when (reason) {
        EmptyReason.NEEDS_INPUT -> EmptyCopy(
            needsInputText ?: context.getString(R.string.search_needs_both),
            null,
        )

        EmptyReason.NO_MATCHING_PLACE -> EmptyCopy(
            context.getString(R.string.place_no_match),
            null,
        )

        // A pack for the date was read and holds no matching journey. This is
        // the only empty answer that is a statement about service.
        EmptyReason.NO_SERVICE_ON_DATE -> EmptyCopy(
            context.getString(R.string.results_empty_no_service_title),
            context.getString(R.string.results_empty_no_service_body),
        )

        // No pack for the date is installed. This is a statement about what
        // this device holds, and saying "no service runs" instead would invent
        // a certainty the data does not support.
        EmptyReason.NO_OFFLINE_DATA_FOR_DATE -> EmptyCopy(
            context.getString(R.string.results_empty_no_offline_title),
            context.getString(R.string.results_empty_no_offline_body),
        )

        EmptyReason.OUTSIDE_COVERAGE -> EmptyCopy(
            context.getString(R.string.results_empty_outside_title),
            context.getString(R.string.results_empty_outside_body),
        )

        EmptyReason.PARTIAL_COVERAGE -> EmptyCopy(
            context.getString(R.string.results_partial_coverage_title),
            context.getString(R.string.results_partial_coverage_body),
        )

        EmptyReason.ORIGIN_EQUALS_DESTINATION -> EmptyCopy(
            context.getString(R.string.search_same_endpoints),
            null,
        )

        EmptyReason.FILTERED_OUT -> EmptyCopy(
            context.getString(R.string.results_empty_filtered_title),
            context.getString(R.string.results_empty_filtered_body),
        )

        EmptyReason.NOTHING_SAVED -> EmptyCopy(
            context.getString(R.string.saved_trips_empty_title),
            context.getString(R.string.saved_trips_empty_body),
        )

        EmptyReason.NOTHING_INSTALLED -> EmptyCopy(
            context.getString(R.string.state_no_data_title),
            context.getString(R.string.state_no_data_body),
        )
    }
}

@Composable
fun failureCopy(reason: FailureReason): EmptyCopy = failureCopyOf(LocalContext.current, reason)

fun failureCopyOf(context: android.content.Context, reason: FailureReason): EmptyCopy {
    return when (reason) {
        FailureReason.NO_DATA_INSTALLED -> EmptyCopy(
            context.getString(R.string.state_no_data_title),
            context.getString(R.string.state_no_data_body),
        )

        FailureReason.RELEASE_MISMATCH -> EmptyCopy(
            context.getString(R.string.state_release_mismatch_title),
            context.getString(R.string.state_release_mismatch_body),
        )

        FailureReason.DATA_UNREADABLE -> EmptyCopy(
            context.getString(R.string.state_unreadable_data_title),
            context.getString(R.string.state_incomplete_data_body),
        )

        FailureReason.OFFLINE -> EmptyCopy(
            context.getString(R.string.state_offline_title),
            context.getString(R.string.state_offline_body),
        )

        FailureReason.SERVER_ERROR -> EmptyCopy(
            context.getString(R.string.state_server_error_title),
            context.getString(R.string.state_server_error_body),
        )

        FailureReason.STORAGE_FULL -> EmptyCopy(
            context.getString(R.string.state_storage_full_title),
            context.getString(R.string.state_storage_full_body),
        )

        FailureReason.PERMISSION_DENIED -> EmptyCopy(
            context.getString(R.string.state_permission_denied_title),
            context.getString(R.string.reminders_permission_denied),
        )

        FailureReason.PERMISSION_REVOKED -> EmptyCopy(
            context.getString(R.string.state_permission_revoked_title),
            context.getString(R.string.reminders_permission_revoked),
        )

        FailureReason.EXPIRED_SERVICE -> EmptyCopy(
            context.getString(R.string.state_expired_title),
            context.getString(R.string.state_expired_body),
        )

        FailureReason.NO_OFFLINE_DATA_FOR_DATE -> EmptyCopy(
            context.getString(R.string.results_empty_no_offline_title),
            context.getString(R.string.results_empty_no_offline_body),
        )

        FailureReason.NOT_FOUND -> EmptyCopy(
            context.getString(R.string.deeplink_unknown_title),
            context.getString(R.string.deeplink_unknown_body),
        )

        FailureReason.INVALID_REQUEST, FailureReason.UNKNOWN -> EmptyCopy(
            context.getString(R.string.state_unknown_error_title),
            context.getString(R.string.state_server_error_body),
        )
    }
}

/**
 * The one place an empty or failed outcome becomes a screen.
 *
 * A failure is announced to accessibility services as an assertive live region,
 * because a screen that silently swaps a list for an error tells a screen-reader
 * user nothing at all.
 */
@Composable
fun OutcomeState(
    title: String,
    body: String?,
    isError: Boolean,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = stringResource(R.string.action_retry),
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val spoken = if (isError) {
        context.getString(R.string.a11y_error_announced, title) +
            (body?.let { ". " + it } ?: "")
    } else {
        title + (body?.let { ". " + it } ?: "")
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Space.x6)
            .semantics(mergeDescendants = true) {
                liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
                contentDescription = spoken
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.SearchOff,
            contentDescription = null,
            tint = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(Space.x3))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(Space.x2))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(Space.x4))
            Button(
                onClick = onRetry,
                modifier = Modifier.sizeIn(minHeight = Space.touchTarget),
            ) {
                Text(retryLabel)
            }
        }
        if (secondaryAction != null) {
            Spacer(Modifier.height(Space.x3))
            secondaryAction()
        }
    }
}

/**
 * Renders any [Loadable] uniformly so no screen can invent a sixth state or
 * quietly leave one of them showing a spinner for ever.
 */
@Composable
fun <T> LoadableContent(
    value: Loadable<T>,
    modifier: Modifier = Modifier,
    needsInputText: String? = null,
    onRetry: (() -> Unit)? = null,
    emptyOverride: (@Composable (EmptyReason) -> Unit)? = null,
    idle: @Composable () -> Unit = {},
    content: @Composable (T) -> Unit,
) {
    when (value) {
        is Loadable.Idle -> idle()
        is Loadable.Loading -> {
            val previous = value.previous
            if (previous != null) content(previous) else LoadingState(modifier)
        }

        is Loadable.Ready -> content(value.value)
        is Loadable.Empty -> {
            if (emptyOverride != null) {
                emptyOverride(value.reason)
            } else {
                val copy = emptyCopy(value.reason, needsInputText)
                OutcomeState(copy.title, copy.body, isError = false, modifier = modifier)
            }
        }

        is Loadable.Failed -> {
            val copy = failureCopy(value.reason)
            OutcomeState(
                title = copy.title,
                body = copy.body,
                isError = true,
                modifier = modifier,
                onRetry = if (value.isRetryable) onRetry else null,
            )
        }
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * A button that never falls below the 48dp accessibility floor, and grows when
 * the person asked for larger touch targets.
 */
@Composable
fun OdivreloButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
    contentDescription: String? = null,
) {
    val extra = LocalExtraTouchPadding.current
    val shared = modifier
        .heightIn(min = Space.touchTarget + extra)
        .then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            },
        )
    if (outlined) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = shared) { Text(text) }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = shared) { Text(text) }
    }
}

/** A row that keeps a visible 48dp target however little content it holds. */
@Composable
fun TouchRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget + LocalExtraTouchPadding.current),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
fun OutlinedBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.medium,
            )
            .padding(Space.x4),
    ) {
        content()
    }
}
