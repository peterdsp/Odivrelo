package dev.peterdsp.poravia.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.peterdsp.poravia.R
import dev.peterdsp.poravia.core.model.JourneyFilters
import dev.peterdsp.poravia.core.model.JourneySort
import dev.peterdsp.poravia.core.model.OperatorCoverageSummary
import dev.peterdsp.poravia.theme.Space
import dev.peterdsp.poravia.ui.adaptive.LocalPoraviaWindow
import dev.peterdsp.poravia.ui.adaptive.avoidFold
import dev.peterdsp.poravia.ui.common.PoraviaButton
import dev.peterdsp.poravia.ui.common.SectionTitle

/**
 * Search filters.
 *
 * Nothing here filters anything itself. The chosen [JourneyFilters] goes to the
 * core, which applies and orders exactly the same way for a pack answer and for
 * a live one, so two identical searches can never be sorted differently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersSheet(
    filters: JourneyFilters,
    operators: List<OperatorCoverageSummary>,
    languageTag: String,
    onApply: (JourneyFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val window = LocalPoraviaWindow.current
    var working by remember(filters) { mutableStateOf(filters) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("filters-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A bottom sheet cannot be split, so on a half-opened device it
                // is kept entirely clear of the seam instead.
                .avoidFold(window.fold)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.x4),
        ) {
            Text(
                text = stringResource(R.string.filters_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )

            SwitchRow(
                label = stringResource(R.string.filter_accessible),
                help = stringResource(R.string.filter_accessible_help),
                checked = working.accessibleOnly,
                testTag = "filter-accessible",
                onChange = { working = working.copy(accessibleOnly = it) },
            )

            SwitchRow(
                label = stringResource(R.string.filter_include_overnight),
                help = null,
                checked = working.includeCrossesMidnight,
                testTag = "filter-overnight",
                onChange = { working = working.copy(includeCrossesMidnight = it) },
            )

            SectionTitle(stringResource(R.string.filter_depart_after))
            HourChoice(
                selected = working.departAfter,
                testTagPrefix = "filter-after",
                onChange = { working = working.copy(departAfter = it) },
            )

            SectionTitle(stringResource(R.string.filter_depart_before))
            HourChoice(
                selected = working.departBefore,
                testTagPrefix = "filter-before",
                onChange = { working = working.copy(departBefore = it) },
            )

            SectionTitle(stringResource(R.string.filter_max_duration))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                (listOf<Int?>(null) + DURATION_CHOICES).forEach { minutes ->
                    FilterChip(
                        selected = working.maxDurationMinutes == minutes,
                        onClick = { working = working.copy(maxDurationMinutes = minutes) },
                        label = {
                            Text(
                                if (minutes == null) {
                                    context.getString(R.string.filter_any)
                                } else {
                                    dev.peterdsp.poravia.ui.common.Formats.duration(
                                        context,
                                        minutes,
                                    )
                                },
                            )
                        },
                        modifier = Modifier.heightIn(min = Space.touchTarget),
                    )
                }
            }

            if (operators.isNotEmpty()) {
                SectionTitle(stringResource(R.string.journey_operator))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    operators.forEach { summary ->
                        val id = summary.operatorId ?: return@forEach
                        val label = summary.name?.resolve(languageTag) ?: id
                        FilterChip(
                            selected = id in working.operatorIds,
                            onClick = {
                                working = working.copy(
                                    operatorIds = if (id in working.operatorIds) {
                                        working.operatorIds - id
                                    } else {
                                        working.operatorIds + id
                                    },
                                )
                            },
                            label = { Text(label) },
                            modifier = Modifier.heightIn(min = Space.touchTarget),
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.filter_sort))
            Column(Modifier.selectableGroup()) {
                JourneySort.entries.forEach { sort ->
                    val label = stringResource(
                        when (sort) {
                            JourneySort.DEPARTURE -> R.string.sort_departure
                            JourneySort.DURATION -> R.string.sort_duration
                            JourneySort.ARRIVAL -> R.string.sort_arrival
                        },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Space.touchTarget)
                            .semantics(mergeDescendants = true) { contentDescription = label },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = working.sort == sort,
                            onClick = { working = working.copy(sort = sort) },
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(Modifier.height(Space.x5))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                TextButton(
                    onClick = { working = JourneyFilters.NONE },
                    modifier = Modifier.heightIn(min = Space.touchTarget),
                ) {
                    Text(stringResource(R.string.action_clear_filters))
                }
                PoraviaButton(
                    text = stringResource(R.string.action_apply),
                    onClick = { onApply(working) },
                    modifier = Modifier.weight(1f).testTag("filters-apply"),
                )
            }
            Spacer(Modifier.height(Space.x8))
        }
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
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun HourChoice(
    selected: String?,
    testTagPrefix: String,
    onChange: (String?) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        (listOf<String?>(null) + HOUR_CHOICES).forEach { hour ->
            FilterChip(
                selected = selected == hour,
                onClick = { onChange(hour) },
                label = {
                    Text(hour ?: context.getString(R.string.filter_any))
                },
                modifier = Modifier
                    .heightIn(min = Space.touchTarget)
                    .testTag(testTagPrefix + "-" + (hour ?: "any")),
            )
        }
    }
}

private val HOUR_CHOICES = listOf("06:00", "09:00", "12:00", "15:00", "18:00", "21:00")
private val DURATION_CHOICES = listOf(90, 120, 180, 240, 360)
