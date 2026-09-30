package dev.peterdsp.poravia.features.format

import dev.peterdsp.poravia.core.model.BoardingRule
import dev.peterdsp.poravia.core.model.Freshness
import dev.peterdsp.poravia.core.model.FreshnessState
import dev.peterdsp.poravia.core.model.Journey
import dev.peterdsp.poravia.core.time.ServiceTime
import kotlin.native.ObjCName
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Presentation values, with no words in them.
 *
 * Every platform formats dates, times and numbers with its own locale-aware
 * formatter and takes its wording from its own string resources. What is shared
 * is the *decision*: which clock reading a departure is, how many hours and
 * minutes a duration breaks into, whether a journey needs a next-day marker.
 *
 * Deciding those once is the point. If each interface derived them from raw
 * timestamps it would eventually derive them differently, and two screens of the
 * same product would disagree about when a coach leaves.
 */

/** A clock reading in `Europe/Athens`, already resolved from an instant. */
@ObjCName("PoraviaClockReading")
data class ClockReading(
    val hour: Int,
    val minute: Int,
    val date: LocalDate,
    /** Offset label as contract 1 writes it, for example `+03:00`. */
    val offsetLabel: String,
    /** True when this reading falls on a later calendar day than its service date. */
    val isNextDay: Boolean,
) {
    val localTime: LocalTime get() = LocalTime(hour, minute)

    /** Minutes past local midnight, 0 to 1439. */
    val minutesOfDay: Int get() = hour * 60 + minute
}

/** A duration split the way every Poravia screen shows it. */
@ObjCName("PoraviaDurationParts")
data class DurationParts(val hours: Int, val minutes: Int) {
    val totalMinutes: Int get() = hours * 60 + minutes
    val hasHours: Boolean get() = hours > 0
}

/** How old a fact is, with the bucket already decided by the core's thresholds. */
@ObjCName("PoraviaFreshnessDisplay")
data class FreshnessDisplay(
    val state: FreshnessState,
    val ageHours: Int,
    /** Whole days, for when hours would be an unhelpfully large number. */
    val ageDays: Int,
    val preferDays: Boolean,
    val isRecent: Boolean,
)

/** Everything a result row needs, decided once. */
@ObjCName("PoraviaJourneyDisplay")
data class JourneyDisplay(
    val departure: ClockReading,
    val arrival: ClockReading,
    val duration: DurationParts,
    val crossesMidnight: Boolean,
    val freshness: FreshnessDisplay,
    val intermediateStopCount: Int,
    val hasIndicativeFare: Boolean,
    /**
     * Null means nobody reviewed the boarding point, which is a different answer
     * from false and must stay different all the way to the screen.
     */
    val stepFreeBoarding: Boolean?,
)

@ObjCName("PoraviaDisplay")
object Display {

    /** Turns an ISO timestamp into an Athens clock reading. */
    fun reading(isoTimestamp: String, serviceDate: String? = null): ClockReading? {
        val instant = ServiceTime.parseInstantOrNull(isoTimestamp) ?: return null
        return reading(instant, serviceDate)
    }

    fun reading(instant: Instant, serviceDate: String? = null): ClockReading {
        val local = ServiceTime.localDateTimeOf(instant)
        val base = ServiceTime.parseServiceDateOrNull(serviceDate)
        return ClockReading(
            hour = local.hour,
            minute = local.minute,
            date = local.date,
            offsetLabel = ServiceTime.offsetLabel(instant),
            isNextDay = base != null && local.date > base,
        )
    }

    fun duration(totalMinutes: Int): DurationParts {
        val safe = if (totalMinutes < 0) 0 else totalMinutes
        return DurationParts(hours = safe / 60, minutes = safe % 60)
    }

    /**
     * Prefers days over hours once a fact is more than two days old, because "62
     * hours" is a number a person has to do arithmetic on and "2 days" is not.
     */
    fun freshness(freshness: Freshness): FreshnessDisplay {
        val hours = if (freshness.ageHours < 0) 0 else freshness.ageHours
        return FreshnessDisplay(
            state = freshness.state,
            ageHours = hours,
            ageDays = hours / 24,
            preferDays = hours >= PREFER_DAYS_ABOVE_HOURS,
            isRecent = hours < 1,
        )
    }

    fun journey(journey: Journey): JourneyDisplay? {
        val departure = reading(journey.departure.at, journey.serviceDate) ?: return null
        val arrival = reading(journey.arrival.at, journey.serviceDate) ?: return null
        return JourneyDisplay(
            departure = departure,
            arrival = arrival,
            duration = duration(journey.durationMinutes),
            crossesMidnight = journey.crossesMidnight,
            freshness = freshness(journey.freshness),
            intermediateStopCount = journey.intermediateStopCount,
            hasIndicativeFare = journey.fare?.isIndicative == true,
            stepFreeBoarding = journey.accessibleBoardingPoint,
        )
    }

    /**
     * Whether a service date is in the past relative to now, so a screen can offer
     * "that day has passed" rather than an empty result list.
     */
    fun isPast(serviceDate: String, now: Instant): Boolean {
        val date = ServiceTime.parseServiceDateOrNull(serviceDate) ?: return false
        return date < ServiceTime.localDateTimeOf(now).date
    }

    fun isToday(serviceDate: String, now: Instant): Boolean =
        serviceDate == ServiceTime.currentServiceDate(now)

    /** A bounded window of dates around today, for a date strip. */
    fun dateWindow(now: Instant, daysBefore: Int, daysAfter: Int): List<String> {
        val today = ServiceTime.currentServiceDate(now)
        return (-daysBefore..daysAfter).map { ServiceTime.shiftServiceDate(today, it) }
    }

    /** True when a stop can actually be boarded at, on request or otherwise. */
    fun canBoard(rule: BoardingRule): Boolean = rule != BoardingRule.NOT_ALLOWED

    fun canAlight(rule: BoardingRule): Boolean = rule != BoardingRule.NOT_ALLOWED

    /** Formats a byte count into a value and a unit a screen can localise. */
    fun bytes(bytes: Long): ByteSize {
        if (bytes < 1024) return ByteSize(bytes.toDouble(), ByteUnit.BYTES)
        val kilobytes = bytes.toDouble() / 1024.0
        if (kilobytes < 1024) return ByteSize(kilobytes, ByteUnit.KILOBYTES)
        val megabytes = kilobytes / 1024.0
        if (megabytes < 1024) return ByteSize(megabytes, ByteUnit.MEGABYTES)
        return ByteSize(megabytes / 1024.0, ByteUnit.GIGABYTES)
    }

    private const val PREFER_DAYS_ABOVE_HOURS = 48
}

@ObjCName("PoraviaByteUnit")
enum class ByteUnit { BYTES, KILOBYTES, MEGABYTES, GIGABYTES }

@ObjCName("PoraviaByteSize")
data class ByteSize(val value: Double, val unit: ByteUnit) {
    /** Bytes are whole; everything else reads better with one decimal. */
    val decimals: Int get() = if (unit == ByteUnit.BYTES) 0 else 1
}
