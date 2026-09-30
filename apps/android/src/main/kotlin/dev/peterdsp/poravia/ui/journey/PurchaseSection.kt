package dev.peterdsp.poravia.ui.journey

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.peterdsp.poravia.R
import dev.peterdsp.poravia.core.model.JourneyDetailBody
import dev.peterdsp.poravia.core.model.PurchaseKind
import dev.peterdsp.poravia.features.Loadable
import dev.peterdsp.poravia.features.settings.PoraviaSettings
import dev.peterdsp.poravia.reminders.ReminderRecord
import dev.peterdsp.poravia.state.AppState
import dev.peterdsp.poravia.state.PoraviaViewModel
import dev.peterdsp.poravia.state.ReminderMessage
import dev.peterdsp.poravia.theme.Space
import dev.peterdsp.poravia.ui.booking.Handoff
import dev.peterdsp.poravia.ui.booking.HandoffResult
import dev.peterdsp.poravia.ui.common.Formats
import dev.peterdsp.poravia.ui.common.InfoPanel
import dev.peterdsp.poravia.ui.common.KeyValueRow
import dev.peterdsp.poravia.ui.common.NoTicketsNotice
import dev.peterdsp.poravia.ui.common.OutlinedBox
import dev.peterdsp.poravia.ui.common.PoraviaButton
import dev.peterdsp.poravia.ui.common.SectionTitle
import dev.peterdsp.poravia.ui.common.Tone

/**
 * Where to buy, and a plain statement that it is not here.
 *
 * Poravia never sells or issues a ticket. An online option opens the operator's
 * own site in a Custom Tab; where there is no online sale the verified ticket
 * office, telephone number, address and opening hours are shown instead, which
 * is the answer a traveller actually needs in a region without online booking.
 */
@Composable
fun PurchaseSection(
    journey: JourneyDetailBody,
    language: String,
    viewModel: PoraviaViewModel,
    snackbarHost: SnackbarHostState,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val purchase = journey.purchase
    var message by rememberSaveable(journey.id) { mutableStateOf<String?>(null) }

    SectionTitle(stringResource(R.string.booking_title))
    NoTicketsNotice(modifier = Modifier.testTag("booking-disclaimer"))
    Spacer(Modifier.height(Space.x3))

    purchase.label?.resolve(language)?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.x2))
    }

    when (purchase.kind) {
        PurchaseKind.ONLINE -> {
            val url = purchase.url
            if (url.isNullOrBlank()) {
                UnavailablePurchase()
            } else {
                PoraviaButton(
                    text = stringResource(R.string.booking_online_action),
                    onClick = {
                        viewModel.beginPurchaseHandoff(journey.id)
                        val outcome = Handoff.openUrl(context, url, primary, surface)
                        message = when (outcome) {
                            HandoffResult.OPENED -> null
                            else -> context.getString(R.string.booking_no_browser)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("booking-online"),
                )
                Spacer(Modifier.height(Space.x2))
                Text(
                    text = stringResource(R.string.booking_leaves_app),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PurchaseKind.ONBOARD -> InfoPanel(
            title = stringResource(R.string.booking_onboard),
            body = null,
            tone = Tone.INFO,
            modifier = Modifier.testTag("booking-onboard"),
        )

        PurchaseKind.TICKET_OFFICE, PurchaseKind.PHONE -> InfoPanel(
            title = stringResource(R.string.booking_office_title),
            body = null,
            tone = Tone.INFO,
            modifier = Modifier.testTag("booking-office"),
        )

        PurchaseKind.UNAVAILABLE -> UnavailablePurchase()
    }

    // The verified fallback is shown whenever the data carries one, including
    // beside an online option: a website that is down does not stop a person
    // walking to a ticket office whose address has been checked.
    if (purchase.hasOfflineFallback || !purchase.openingHours.isNullOrBlank()) {
        Spacer(Modifier.height(Space.x3))
        OutlinedBox(modifier = Modifier.testTag("booking-fallback")) {
            Column {
                purchase.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                    KeyValueRow(stringResource(R.string.booking_phone), phone)
                    PoraviaButton(
                        text = stringResource(R.string.booking_call),
                        onClick = {
                            val outcome = Handoff.dial(context, phone)
                            message = if (outcome == HandoffResult.OPENED) {
                                null
                            } else {
                                context.getString(R.string.booking_no_dialer)
                            }
                        },
                        outlined = true,
                        modifier = Modifier.fillMaxWidth().testTag("booking-call"),
                    )
                }
                purchase.address?.takeIf { it.isNotBlank() }?.let { address ->
                    KeyValueRow(stringResource(R.string.booking_address), address)
                }
                purchase.openingHours?.takeIf { it.isNotBlank() }?.let { hours ->
                    KeyValueRow(stringResource(R.string.booking_hours), hours)
                }
            }
        }
    }

    purchase.disclaimer?.resolve(language)?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(Space.x2))
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    message?.let {
        Spacer(Modifier.height(Space.x2))
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics {
                contentDescription = context.getString(R.string.a11y_error_announced, it)
            },
        )
    }
}

@Composable
private fun UnavailablePurchase() {
    InfoPanel(
        title = stringResource(R.string.booking_unavailable_title),
        body = stringResource(R.string.booking_unavailable_body),
        tone = Tone.WARNING,
        modifier = Modifier.testTag("booking-unavailable"),
    )
}

/**
 * Saving a trip for offline reading, and the opt-in departure reminder that
 * goes with it.
 *
 * The reminder is off until asked for, the notification permission is requested
 * in that moment and nowhere else, and the payload carries only the boarding
 * point and the departure time.
 */
@Composable
fun SavedTripSection(
    state: AppState,
    viewModel: PoraviaViewModel,
    journeyId: String,
    serviceDate: String,
) {
    val context = LocalContext.current
    val locale = dev.peterdsp.poravia.ui.currentLocale()
    val language = state.settings.resolvedLanguageTag
    val saved = state.savedTrips.valueOrNull?.firstOrNull {
        it.journeyId == journeyId && it.serviceDate == serviceDate
    }
    val reminder = saved?.let { trip ->
        state.reminders.firstOrNull { it.savedTripId == trip.id }
    }
    val journey = (state.journey as? Loadable.Ready)?.value?.detail?.journey

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshNotificationPermission() }

    SectionTitle(stringResource(R.string.trip_ready_title))
    Text(
        text = stringResource(R.string.trip_ready_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Space.x3))

    PoraviaButton(
        text = stringResource(
            if (saved == null) R.string.journey_save else R.string.journey_unsave,
        ),
        onClick = {
            if (saved == null) {
                viewModel.saveTrip(journeyId, serviceDate)
            } else {
                viewModel.removeSavedTrip(saved.id)
            }
        },
        outlined = saved != null,
        modifier = Modifier.fillMaxWidth().testTag("journey-save"),
    )

    if (saved != null && journey != null) {
        Spacer(Modifier.height(Space.x4))
        SectionTitle(stringResource(R.string.reminders_title))
        Text(
            text = stringResource(R.string.reminders_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.x2))
        Text(
            text = stringResource(R.string.reminders_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.x3))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Space.touchTarget)
                .semantics(mergeDescendants = true) {
                    contentDescription = context.getString(R.string.reminders_enable)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reminders_enable),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = reminder != null,
                onCheckedChange = { wanted ->
                    if (!wanted) {
                        viewModel.cancelReminder(saved.id)
                        return@Switch
                    }
                    if (!state.notificationsPermitted &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@Switch
                    }
                    viewModel.scheduleReminder(
                        ReminderRecord(
                            savedTripId = saved.id,
                            journeyId = journeyId,
                            serviceDate = serviceDate,
                            departureAt = journey.departure.at,
                            leadMinutes = state.settings.reminderLeadMinutes,
                            boardingLabel = listOfNotNull(
                                journey.boardingPoint?.name?.resolve(language)
                                    ?: journey.departure.stopName.resolve(language),
                                journey.boardingPoint?.bay?.let {
                                    context.getString(R.string.journey_boarding_bay, it)
                                },
                            ).joinToString(", "),
                            departureLabel = Formats.time(context, journey.departure.at),
                        ),
                    )
                },
                modifier = Modifier.testTag("reminder-switch"),
            )
        }

        if (reminder == null) {
            Spacer(Modifier.height(Space.x2))
            Text(
                text = stringResource(R.string.reminders_lead_label),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(Space.x2))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                PoraviaSettings.REMINDER_LEAD_CHOICES.take(4).forEach { minutes ->
                    FilterChip(
                        selected = state.settings.reminderLeadMinutes == minutes,
                        onClick = { viewModel.setReminderLead(minutes) },
                        label = {
                            Text(context.getString(R.string.reminders_lead_minutes, minutes))
                        },
                        modifier = Modifier.heightIn(min = Space.touchTarget),
                    )
                }
            }
        }

        if (!state.notificationsPermitted) {
            Spacer(Modifier.height(Space.x3))
            InfoPanel(
                title = stringResource(R.string.reminders_permission_title),
                body = stringResource(R.string.reminders_permission_body),
                tone = Tone.WARNING,
                modifier = Modifier.testTag("reminder-permission"),
            ) {
                PoraviaButton(
                    text = stringResource(R.string.reminders_permission_settings),
                    onClick = { Handoff.openSystemNotificationSettings(context) },
                    outlined = true,
                )
            }
        }

        when (val outcome = state.reminderMessage) {
            null -> Unit
            is ReminderMessage.Scheduled -> ReminderNote(
                context.getString(R.string.reminders_scheduled, outcome.at),
                Tone.SUCCESS,
            )

            ReminderMessage.Cancelled -> ReminderNote(
                stringResource(R.string.reminders_cancelled),
                Tone.NEUTRAL,
            )

            ReminderMessage.InThePast -> ReminderNote(
                stringResource(R.string.reminders_in_the_past),
                Tone.WARNING,
            )

            ReminderMessage.PermissionDenied -> ReminderNote(
                stringResource(R.string.reminders_permission_denied),
                Tone.WARNING,
            )

            ReminderMessage.PermissionRevoked -> ReminderNote(
                stringResource(R.string.reminders_permission_revoked),
                Tone.WARNING,
            )
        }

        Spacer(Modifier.height(Space.x3))
        Text(
            text = context.getString(
                R.string.saved_trip_cached,
                Formats.timestamp(saved.cachedAt, locale).orEmpty(),
                saved.releaseId,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReminderNote(text: String, tone: Tone) {
    Spacer(Modifier.height(Space.x3))
    InfoPanel(title = text, body = null, tone = tone, modifier = Modifier.testTag("reminder-note"))
}
