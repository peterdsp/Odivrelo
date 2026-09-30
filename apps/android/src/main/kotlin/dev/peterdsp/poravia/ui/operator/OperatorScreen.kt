package dev.peterdsp.poravia.ui.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.peterdsp.poravia.R
import dev.peterdsp.poravia.state.AppState
import dev.peterdsp.poravia.state.PoraviaViewModel
import dev.peterdsp.poravia.theme.Space
import dev.peterdsp.poravia.ui.booking.Handoff
import dev.peterdsp.poravia.ui.booking.HandoffResult
import dev.peterdsp.poravia.ui.common.Badge
import dev.peterdsp.poravia.ui.common.CoverageBadge
import dev.peterdsp.poravia.ui.common.DemoNotice
import dev.peterdsp.poravia.ui.common.Formats
import dev.peterdsp.poravia.ui.common.KeyValueRow
import dev.peterdsp.poravia.ui.common.LoadableContent
import dev.peterdsp.poravia.ui.common.OutlinedBox
import dev.peterdsp.poravia.ui.common.PoraviaButton
import dev.peterdsp.poravia.ui.common.PoraviaScreen
import dev.peterdsp.poravia.ui.common.RightsBadge
import dev.peterdsp.poravia.ui.common.SectionTitle
import dev.peterdsp.poravia.ui.common.Tone
import dev.peterdsp.poravia.ui.currentLocale

/**
 * One operator, with its attribution, its coverage and a way to report a
 * mistake.
 *
 * Coverage is stated in both directions: what this release holds for the
 * operator, and, through the rights state on each source, whether that material
 * may be shown at all.
 */
@Composable
fun OperatorScreen(
    operatorId: String,
    state: AppState,
    viewModel: PoraviaViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(operatorId) {
        if (operatorId.isNotBlank()) viewModel.loadOperator(operatorId)
    }
    val context = LocalContext.current
    val locale = currentLocale()
    val language = state.settings.resolvedLanguageTag
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    var message by rememberSaveable(operatorId) { mutableStateOf<String?>(null) }

    PoraviaScreen(title = stringResource(R.string.operator_title), onBack = onBack) { padding ->
        LoadableContent(
            value = state.operator,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { detail ->
            val operator = detail.operator
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
                Spacer(Modifier.height(Space.x4))
                Text(
                    text = operator.name.resolve(language),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag("operator-name"),
                )
                operator.federationNumber?.let {
                    Text(
                        text = "#" + it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionTitle(stringResource(R.string.operator_coverage_title))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    CoverageBadge(operator.coverage.state)
                    Badge(
                        context.getString(
                            R.string.operator_routes_count,
                            operator.coverage.routeCount,
                        ),
                        Tone.NEUTRAL,
                    )
                    Badge(
                        context.getString(
                            R.string.operator_stops_count,
                            operator.coverage.stopCount,
                        ),
                        Tone.NEUTRAL,
                    )
                }
                Formats.timestamp(operator.verifiedAt, locale)?.let {
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        text = context.getString(R.string.operator_verified_at, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionTitle(stringResource(R.string.operator_contact_title))
                val contact = operator.contact
                if (contact?.phone.isNullOrBlank() &&
                    contact?.email.isNullOrBlank() &&
                    contact?.address.isNullOrBlank() &&
                    operator.officialSiteUrl.isNullOrBlank()
                ) {
                    Text(
                        text = stringResource(R.string.operator_no_site),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedBox {
                        Column {
                            contact?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                                KeyValueRow(stringResource(R.string.booking_phone), phone)
                                PoraviaButton(
                                    text = stringResource(R.string.booking_call),
                                    onClick = {
                                        message = if (
                                            Handoff.dial(context, phone) == HandoffResult.OPENED
                                        ) {
                                            null
                                        } else {
                                            context.getString(R.string.booking_no_dialer)
                                        }
                                    },
                                    outlined = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            contact?.email?.takeIf { it.isNotBlank() }?.let { email ->
                                KeyValueRow(stringResource(R.string.operator_email), email)
                            }
                            contact?.address?.takeIf { it.isNotBlank() }?.let { address ->
                                KeyValueRow(stringResource(R.string.booking_address), address)
                            }
                            operator.officialSiteUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                Spacer(Modifier.height(Space.x2))
                                PoraviaButton(
                                    text = stringResource(R.string.operator_site),
                                    onClick = {
                                        message = if (
                                            Handoff.openUrl(context, url, primary, surface) ==
                                            HandoffResult.OPENED
                                        ) {
                                            null
                                        } else {
                                            context.getString(R.string.booking_no_browser)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("operator-site"),
                                )
                            }
                        }
                    }
                }

                SectionTitle(stringResource(R.string.operator_sources_title))
                if (operator.sources.isEmpty()) {
                    Text(
                        text = stringResource(R.string.rights_unknown_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    operator.sources.forEach { provenance ->
                        OutlinedBox(modifier = Modifier.padding(vertical = Space.x1)) {
                            Column {
                                Text(
                                    text = provenance.sourceName,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(Space.x2))
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                                    RightsBadge(provenance.rightsStatus)
                                    provenance.licence?.let {
                                        Badge(
                                            context.getString(R.string.sources_licence, it),
                                            Tone.NEUTRAL,
                                        )
                                    }
                                }
                                Formats.timestamp(provenance.retrievedAt, locale)?.let {
                                    Spacer(Modifier.height(Space.x1))
                                    Text(
                                        text = context.getString(R.string.sources_retrieved, it),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                operator.correctionUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    Spacer(Modifier.height(Space.x5))
                    PoraviaButton(
                        text = stringResource(R.string.journey_correction),
                        onClick = {
                            message = if (
                                Handoff.openUrl(context, url, primary, surface) ==
                                HandoffResult.OPENED
                            ) {
                                null
                            } else {
                                context.getString(R.string.booking_no_browser)
                            }
                        },
                        outlined = true,
                        modifier = Modifier.fillMaxWidth().testTag("operator-correction"),
                    )
                }

                message?.let {
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(Space.x10))
            }
        }
    }
}
