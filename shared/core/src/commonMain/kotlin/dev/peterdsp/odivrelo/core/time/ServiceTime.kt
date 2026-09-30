package dev.peterdsp.odivrelo.core.time

import dev.peterdsp.odivrelo.core.model.Freshness
import dev.peterdsp.odivrelo.core.model.FreshnessState
import kotlin.math.abs
import kotlin.native.ObjCName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Every service-date, time-zone and midnight-crossing decision in Odivrelo is
 * resolved here and nowhere else. No user interface reimplements a timetable
 * engine, and no screen recomputes a service date from an arrival instant.
 *
 * The product's operating time zone is `Europe/Athens`, which observes EET
 * (UTC+02:00) in winter and EEST (UTC+03:00) in summer. In 2026 the changes
 * are 2026-03-29 (02:59:59 EET is followed by 04:00:00 EEST, so 03:00 local
 * does not exist) and 2026-10-25 (03:59:59 EEST is followed by 03:00:00 EET,
 * so 03:00 to 03:59 local happens twice).
 */
@ObjCName("OdivreloServiceTime")
object ServiceTime {

    /** The single operating time zone of the published data. */
    val zone: TimeZone = TimeZone.of("Europe/Athens")

    const val SERVICE_DATE_LENGTH: Int = 10

    /**
     * Freshness thresholds. A result checked within 24 hours is fresh, within
     * seven days is aging, and older than that is stale and labelled as such.
     */
    val FRESH_LIMIT: Duration = 24.hours
    val AGING_LIMIT: Duration = (24 * 7).hours

    fun parseServiceDate(serviceDate: String): LocalDate {
        require(serviceDate.length == SERVICE_DATE_LENGTH) {
            "service date must be YYYY-MM-DD, got '$serviceDate'"
        }
        return LocalDate.parse(serviceDate)
    }

    fun parseServiceDateOrNull(serviceDate: String?): LocalDate? =
        runCatching { serviceDate?.let { parseServiceDate(it) } }.getOrNull()

    fun formatServiceDate(date: LocalDate): String = date.toString()

    /** The service date that is current in Athens at [now]. */
    fun currentServiceDate(now: Instant): String =
        now.toLocalDateTime(zone).date.toString()

    /**
     * The service date a departure belongs to. This is the local Athens date of
     * the departure instant. An arrival after midnight never moves it.
     */
    fun serviceDateOfDeparture(departure: Instant): String =
        departure.toLocalDateTime(zone).date.toString()

    /**
     * True when the journey is still running at local Athens midnight. Computed
     * from the two local dates, never from a duration threshold.
     */
    fun crossesMidnight(departure: Instant, arrival: Instant): Boolean =
        arrival.toLocalDateTime(zone).date > departure.toLocalDateTime(zone).date

    /**
     * Elapsed minutes between two instants. Because this works on instants and
     * not on local clock readings, an overnight journey through a DST change is
     * neither one hour too long nor one hour too short.
     */
    fun durationMinutes(departure: Instant, arrival: Instant): Int =
        (arrival - departure).inWholeMinutes.toInt()

    /**
     * Resolves a local wall-clock time on a service date to a real instant.
     *
     * On a spring-forward gap the requested local time does not exist; the
     * instant immediately after the gap is returned, which is what an operator
     * means by "the 03:00 departure" on that morning. On an autumn overlap the
     * first (summer-time) occurrence is returned, which is the earlier real
     * instant and therefore the one a traveller must be at the bay for.
     */
    fun instantAt(serviceDate: String, localTime: LocalTime): Instant =
        instantAt(parseServiceDate(serviceDate), localTime)

    fun instantAt(date: LocalDate, localTime: LocalTime): Instant =
        LocalDateTime(date, localTime).toInstant(zone)

    /**
     * True when the given local wall-clock reading never happens on that date
     * in Athens because the clocks jumped forward over it.
     */
    fun isInDaylightSavingGap(date: LocalDate, localTime: LocalTime): Boolean {
        val wanted = LocalDateTime(date, localTime)
        return wanted.toInstant(zone).toLocalDateTime(zone) != wanted
    }

    /**
     * True when the given local wall-clock reading happens twice on that date
     * in Athens because the clocks went back over it. The instant returned by
     * [instantAt] is the first, summer-time occurrence.
     */
    fun isAmbiguousLocalTime(date: LocalDate, localTime: LocalTime): Boolean {
        val wanted = LocalDateTime(date, localTime)
        val first = wanted.toInstant(zone)
        if (first.toLocalDateTime(zone) != wanted) return false
        val later = first.plus(1, DateTimeUnit.HOUR)
        return later.toLocalDateTime(zone) == wanted
    }

    /**
     * Resolves a departure expressed as a service date plus a local clock time
     * that may pass midnight, using the operator convention that `25:30` on
     * 2026-10-02 means 01:30 on 2026-10-03 while still belonging to the
     * 2026-10-02 service day.
     */
    fun instantAtExtendedTime(serviceDate: String, hour: Int, minute: Int): Instant {
        require(hour in 0..47) { "extended hour must be 0..47, got $hour" }
        require(minute in 0..59) { "minute must be 0..59, got $minute" }
        val baseDate = parseServiceDate(serviceDate)
        val dayOffset = hour / 24
        val localHour = hour % 24
        val date = if (dayOffset == 0) baseDate else baseDate.plus(DatePeriod(days = dayOffset))
        return instantAt(date, LocalTime(localHour, minute))
    }

    fun localDateTimeOf(instant: Instant): LocalDateTime = instant.toLocalDateTime(zone)

    fun localTimeOf(instant: Instant): LocalTime = instant.toLocalDateTime(zone).time

    /** `+02:00` or `+03:00` for the given instant, as the contract requires. */
    fun offsetLabel(instant: Instant): String {
        val seconds = zone.offsetAt(instant).totalSeconds
        val sign = if (seconds < 0) "-" else "+"
        val absolute = abs(seconds)
        val hours = absolute / 3600
        val minutes = (absolute % 3600) / 60
        return sign + pad2(hours) + ":" + pad2(minutes)
    }

    /** ISO-8601 with the explicit Athens offset, exactly as contract 1 wants. */
    fun formatIsoWithAthensOffset(instant: Instant): String {
        val local = instant.toLocalDateTime(zone)
        return buildString {
            append(local.date.toString())
            append('T')
            append(pad2(local.hour))
            append(':')
            append(pad2(local.minute))
            append(':')
            append(pad2(local.second))
            append(offsetLabel(instant))
        }
    }

    fun parseInstant(value: String): Instant = Instant.parse(value)

    fun parseInstantOrNull(value: String?): Instant? =
        if (value.isNullOrBlank()) null else runCatching { Instant.parse(value) }.getOrNull()

    fun shiftServiceDate(serviceDate: String, days: Int): String {
        val date = parseServiceDate(serviceDate)
        return if (days >= 0) {
            date.plus(DatePeriod(days = days)).toString()
        } else {
            date.minus(DatePeriod(days = -days)).toString()
        }
    }

    fun startOfServiceDay(serviceDate: String): Instant =
        parseServiceDate(serviceDate).atStartOfDayIn(zone)

    /**
     * The half-open instant window a service day occupies in Athens. On a DST
     * day this window is 23 or 25 hours long, which is why it is derived from
     * the zone and not from a constant.
     */
    fun serviceDayWindow(serviceDate: String): Pair<Instant, Instant> {
        val start = startOfServiceDay(serviceDate)
        val end = startOfServiceDay(shiftServiceDate(serviceDate, 1))
        return start to end
    }

    fun serviceDayLengthMinutes(serviceDate: String): Int {
        val (start, end) = serviceDayWindow(serviceDate)
        return (end - start).inWholeMinutes.toInt()
    }

    /**
     * Freshness of a checked-at timestamp relative to now. Both are parsed as
     * instants, so a client in another zone gets the same answer.
     */
    fun freshness(checkedAt: String, now: String): Freshness {
        val checkedInstant = parseInstantOrNull(checkedAt)
        val nowInstant = parseInstantOrNull(now)
        if (checkedInstant == null || nowInstant == null) {
            return Freshness(checkedAt = checkedAt, ageHours = 0, state = FreshnessState.STALE)
        }
        return freshness(checkedInstant, nowInstant)
    }

    fun freshness(checkedAt: Instant, now: Instant): Freshness {
        val age = now - checkedAt
        // A timestamp from the future is a clock disagreement, not freshness.
        val normalised = if (age.isNegative()) Duration.ZERO else age
        val state = when {
            normalised <= FRESH_LIMIT -> FreshnessState.FRESH
            normalised <= AGING_LIMIT -> FreshnessState.AGING
            else -> FreshnessState.STALE
        }
        return Freshness(
            checkedAt = checkedAt.toString(),
            ageHours = normalised.inWholeHours.toInt(),
            state = state,
        )
    }

    private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()
}
