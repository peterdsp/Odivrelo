package dev.peterdsp.odivrelo.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.peterdsp.odivrelo.MainActivity
import dev.peterdsp.odivrelo.R
import dev.peterdsp.odivrelo.core.Brand
import dev.peterdsp.odivrelo.core.time.ServiceTime
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * One opted-in departure reminder.
 *
 * Read the fields carefully: there is no passenger name, no booking reference,
 * no ticket identifier and no barcode here, and there is nowhere for one to be
 * added by accident. The notification is built from exactly these fields, so
 * the privacy promise is a property of the data shape rather than of someone
 * remembering it.
 *
 * [departureAt] is an absolute instant. A device that changes time zone, or
 * flies somewhere else, does not move the reminder: the coach still leaves when
 * it leaves. The label a person reads is always Athens local time, computed by
 * the core.
 */
@Serializable
data class ReminderRecord(
    val savedTripId: String,
    val journeyId: String,
    val serviceDate: String,
    val departureAt: String,
    val leadMinutes: Int,
    val boardingLabel: String,
    val departureLabel: String,
) {
    val triggerAtMillis: Long
        get() = (ServiceTime.parseInstant(departureAt).toEpochMilliseconds()) -
            (leadMinutes.toLong() * 60_000L)

    /** A stable, positive request code derived only from the saved-trip id. */
    val requestCode: Int get() = savedTripId.hashCode() and 0x7FFFFFFF
}

private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "odivrelo_reminders",
)

/**
 * The reminders the person asked for, and nothing else.
 *
 * Persisted rather than held in memory so that a reboot, a process death or an
 * application update can rebuild exactly the same alarms.
 */
class ReminderStore(context: Context) {

    private val store = context.applicationContext.reminderDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val reminders: Flow<List<ReminderRecord>> = store.data.map { decode(it[KEY]) }

    suspend fun all(): List<ReminderRecord> = reminders.first()

    suspend fun put(record: ReminderRecord) {
        store.edit { preferences ->
            val next = decode(preferences[KEY])
                .filterNot { it.savedTripId == record.savedTripId } + record
            preferences[KEY] = encode(next)
        }
    }

    suspend fun remove(savedTripId: String) {
        store.edit { preferences ->
            preferences[KEY] = encode(
                decode(preferences[KEY]).filterNot { it.savedTripId == savedTripId },
            )
        }
    }

    suspend fun clear() {
        store.edit { preferences -> preferences[KEY] = encode(emptyList()) }
    }

    private fun decode(raw: String?): List<ReminderRecord> = raw?.let {
        runCatching { json.decodeFromString(ListSerializer(ReminderRecord.serializer()), it) }
            .getOrDefault(emptyList())
    } ?: emptyList()

    private fun encode(records: List<ReminderRecord>): String =
        json.encodeToString(ListSerializer(ReminderRecord.serializer()), records)

    private companion object {
        val KEY = stringPreferencesKey("reminders_v1")
    }
}

/** What happened when a reminder was asked for, in terms a screen can state. */
sealed interface ReminderOutcome {
    data class Scheduled(val record: ReminderRecord) : ReminderOutcome

    /** The trigger time has already gone by, so nothing was scheduled. */
    data object InThePast : ReminderOutcome

    /** The notification permission has not been granted. */
    data object PermissionMissing : ReminderOutcome
}

/**
 * Schedules, reschedules and cancels local departure reminders.
 *
 * Two mechanisms are used together on purpose. `AlarmManager` gives the closest
 * thing to the right minute without asking for the exact-alarm permission,
 * which this application has no business holding. A `WorkManager` request at the
 * same instant is the backstop: `setAndAllowWhileIdle` can be deferred, and a
 * reminder that silently never arrives is worse than one that arrives late.
 * Both paths end in [ReminderNotifier], which fires a given reminder once.
 */
class ReminderScheduler(
    private val context: Context,
    private val store: ReminderStore,
) {

    private val appContext = context.applicationContext

    fun hasNotificationPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        }
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        // A permission that was granted and then switched off in system settings
        // is granted at the package level but disabled for delivery. Both have to
        // be true for a reminder to actually appear.
        return granted && NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    suspend fun schedule(record: ReminderRecord, now: Instant = Clock.System.now()): ReminderOutcome {
        if (!hasNotificationPermission()) return ReminderOutcome.PermissionMissing
        if (record.triggerAtMillis <= now.toEpochMilliseconds()) return ReminderOutcome.InThePast
        store.put(record)
        arm(record)
        return ReminderOutcome.Scheduled(record)
    }

    suspend fun cancel(savedTripId: String) {
        val existing = store.all().firstOrNull { it.savedTripId == savedTripId }
        store.remove(savedTripId)
        if (existing != null) disarm(existing)
    }

    suspend fun reminderFor(savedTripId: String): ReminderRecord? =
        store.all().firstOrNull { it.savedTripId == savedTripId }

    /**
     * Rebuilds every alarm from the stored records.
     *
     * Called after a reboot, after the package is replaced, and after the device
     * time zone or clock changes. Because a record holds an absolute instant, a
     * time-zone change does not move a reminder; rescheduling simply makes sure
     * the platform still holds an alarm for it.
     */
    suspend fun rescheduleAll(now: Instant = Clock.System.now()) {
        val records = store.all()
        if (!hasNotificationPermission()) {
            // The permission was taken away while the application was not
            // running. Leaving alarms armed would produce silent failures, so
            // they are dropped and the settings screen says the reminders were
            // cancelled.
            records.forEach { disarm(it) }
            store.clear()
            return
        }
        records.forEach { record ->
            if (record.triggerAtMillis <= now.toEpochMilliseconds()) {
                store.remove(record.savedTripId)
                disarm(record)
            } else {
                arm(record)
            }
        }
    }

    private fun arm(record: ReminderRecord) {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(record, create = true) ?: return
        runCatching {
            alarmManager?.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                record.triggerAtMillis,
                pending,
            )
        }

        val delay = max(0L, record.triggerAtMillis - System.currentTimeMillis())
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            workName(record.savedTripId),
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(ReminderWorker.KEY_SAVED_TRIP_ID, record.savedTripId)
                        .build(),
                )
                .build(),
        )
    }

    private fun disarm(record: ReminderRecord) {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        pendingIntent(record, create = false)?.let { pending ->
            runCatching { alarmManager?.cancel(pending) }
            pending.cancel()
        }
        WorkManager.getInstance(appContext).cancelUniqueWork(workName(record.savedTripId))
        NotificationManagerCompat.from(appContext).cancel(record.requestCode)
    }

    private fun pendingIntent(record: ReminderRecord, create: Boolean): PendingIntent? {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_SAVED_TRIP_ID, record.savedTripId)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        if (!create) flags = flags or PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(appContext, record.requestCode, intent, flags)
    }

    companion object {
        const val ACTION_FIRE: String = Brand.ANDROID_APPLICATION_ID + ".REMINDER_FIRE"
        const val EXTRA_SAVED_TRIP_ID: String = "savedTripId"

        fun workName(savedTripId: String): String = "reminder-" + savedTripId
    }
}

/**
 * Turns a stored record into the one notification it is allowed to be.
 *
 * The title carries the lead time, the body carries the boarding point and the
 * departure time in Athens local time. Nothing else is available to it, because
 * nothing else is in the record.
 */
object ReminderNotifier {

    const val CHANNEL_ID: String = "departures"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun notify(context: Context, record: ReminderRecord) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            record.requestCode,
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                // The saved-trip screen, addressed the same way any deep link is.
                data = android.net.Uri.parse(Brand.DEEP_LINK_SCHEME_PREFIX + "trips")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(R.string.reminder_notification_title, record.leadMinutes),
            )
            .setContentText(
                context.getString(
                    R.string.reminder_notification_body,
                    record.boardingLabel,
                    record.departureLabel,
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Public, because there is nothing in it to hide. That is the point.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        runCatching { manager.notify(record.requestCode, notification) }
    }
}

/** The alarm path. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_SAVED_TRIP_ID) ?: return
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                val store = ReminderStore(appContext)
                runBlocking {
                    val record = store.all().firstOrNull { it.savedTripId == id }
                    if (record != null) {
                        ReminderNotifier.notify(appContext, record)
                        // Fired, so it is done. The work backstop for the same id
                        // finds nothing and does nothing.
                        store.remove(id)
                        WorkManager.getInstance(appContext)
                            .cancelUniqueWork(ReminderScheduler.workName(id))
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** The backstop path, for an alarm the system deferred past its moment. */
class ReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : Worker(context, parameters) {

    override fun doWork(): Result {
        val id = inputData.getString(KEY_SAVED_TRIP_ID) ?: return Result.success()
        val store = ReminderStore(applicationContext)
        runBlocking {
            val record = store.all().firstOrNull { it.savedTripId == id } ?: return@runBlocking
            ReminderNotifier.notify(applicationContext, record)
            store.remove(id)
        }
        return Result.success()
    }

    companion object {
        const val KEY_SAVED_TRIP_ID: String = "savedTripId"
    }
}

/**
 * Reboot, package replacement, time-zone change and clock change all invalidate
 * the platform's idea of when an alarm should fire, so each of them rebuilds
 * every reminder from the stored records.
 */
class SystemStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit

            else -> return
        }
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                runBlocking {
                    withContext(Dispatchers.IO) {
                        val store = ReminderStore(appContext)
                        ReminderScheduler(appContext, store).rescheduleAll()
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
