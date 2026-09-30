package dev.peterdsp.poravia.core

import dev.peterdsp.poravia.core.model.FreshnessState
import dev.peterdsp.poravia.core.time.ServiceTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The service-date and time-zone rules, asserted once for the whole product.
 *
 * Greece changes clocks on 2026-03-29 (02:59:59 EET is followed by 04:00:00
 * EEST) and on 2026-10-25 (03:59:59 EEST is followed by 03:00:00 EET). Both
 * dates are tested directly rather than through a generic "DST works" claim.
 */
class ServiceTimeTest {

    @Test
    fun athensSummerOffsetIsThreeHours() {
        val instant = ServiceTime.instantAt("2026-10-02", LocalTime(9, 0))
        assertEquals("+03:00", ServiceTime.offsetLabel(instant))
        assertEquals("2026-10-02T09:00:00+03:00", ServiceTime.formatIsoWithAthensOffset(instant))
    }

    @Test
    fun athensWinterOffsetIsTwoHours() {
        val instant = ServiceTime.instantAt("2026-11-10", LocalTime(9, 0))
        assertEquals("+02:00", ServiceTime.offsetLabel(instant))
        assertEquals("2026-11-10T09:00:00+02:00", ServiceTime.formatIsoWithAthensOffset(instant))
    }

    @Test
    fun overnightJourneyKeepsTheEarlierServiceDate() {
        // 23:30 on 2026-10-02 arriving 01:40 on 2026-10-03.
        val departure = ServiceTime.instantAtExtendedTime("2026-10-02", 23, 30)
        val arrival = ServiceTime.instantAtExtendedTime("2026-10-02", 25, 40)

        assertEquals("2026-10-02", ServiceTime.serviceDateOfDeparture(departure))
        assertEquals("2026-10-03", ServiceTime.localDateTimeOf(arrival).date.toString())
        assertTrue(ServiceTime.crossesMidnight(departure, arrival))
        assertEquals(130, ServiceTime.durationMinutes(departure, arrival))
    }

    @Test
    fun sameDayJourneyDoesNotCrossMidnight() {
        val departure = ServiceTime.instantAtExtendedTime("2026-10-02", 9, 0)
        val arrival = ServiceTime.instantAtExtendedTime("2026-10-02", 12, 10)
        assertFalse(ServiceTime.crossesMidnight(departure, arrival))
        assertEquals(190, ServiceTime.durationMinutes(departure, arrival))
    }

    @Test
    fun journeyArrivingExactlyAtMidnightDoesNotCrossIt() {
        val departure = ServiceTime.instantAtExtendedTime("2026-10-02", 22, 0)
        val arrival = ServiceTime.instantAtExtendedTime("2026-10-02", 24, 0)
        // 24:00 is 00:00 the next calendar day, so this does cross midnight.
        assertTrue(ServiceTime.crossesMidnight(departure, arrival))
        assertEquals(120, ServiceTime.durationMinutes(departure, arrival))

        val earlier = ServiceTime.instantAtExtendedTime("2026-10-02", 23, 59)
        assertFalse(ServiceTime.crossesMidnight(departure, earlier))
    }

    @Test
    fun springForwardGapIsRecognisedAndResolvedForwards() {
        val date = LocalDate.parse("2026-03-29")
        // 03:00 to 03:59 local does not exist on this date in Athens.
        assertTrue(ServiceTime.isInDaylightSavingGap(date, LocalTime(3, 0)))
        assertTrue(ServiceTime.isInDaylightSavingGap(date, LocalTime(3, 30)))
        assertFalse(ServiceTime.isInDaylightSavingGap(date, LocalTime(2, 45)))
        assertFalse(ServiceTime.isInDaylightSavingGap(date, LocalTime(4, 0)))

        // A departure listed as 03:00 resolves to the first real instant after
        // the jump, which is 04:00 EEST.
        val resolved = ServiceTime.instantAt(date, LocalTime(3, 0))
        assertEquals("2026-03-29T04:00:00+03:00", ServiceTime.formatIsoWithAthensOffset(resolved))
    }

    @Test
    fun springForwardDayIsTwentyThreeHoursLong() {
        assertEquals(23 * 60, ServiceTime.serviceDayLengthMinutes("2026-03-29"))
    }

    @Test
    fun journeyThroughSpringForwardIsNotAnHourTooLong() {
        // 02:45 EET to 05:00 EEST. On the clock that is 2 hours 15 minutes, but
        // only 1 hour 15 minutes of real time passes.
        val departure = ServiceTime.instantAtExtendedTime("2026-03-29", 2, 45)
        val arrival = ServiceTime.instantAtExtendedTime("2026-03-29", 5, 0)
        assertEquals("2026-03-29T02:45:00+02:00", ServiceTime.formatIsoWithAthensOffset(departure))
        assertEquals("2026-03-29T05:00:00+03:00", ServiceTime.formatIsoWithAthensOffset(arrival))
        assertEquals(75, ServiceTime.durationMinutes(departure, arrival))
        assertFalse(ServiceTime.crossesMidnight(departure, arrival))
    }

    @Test
    fun autumnOverlapIsRecognisedAndResolvesToTheFirstOccurrence() {
        val date = LocalDate.parse("2026-10-25")
        assertTrue(ServiceTime.isAmbiguousLocalTime(date, LocalTime(3, 0)))
        assertTrue(ServiceTime.isAmbiguousLocalTime(date, LocalTime(3, 59)))
        assertFalse(ServiceTime.isAmbiguousLocalTime(date, LocalTime(2, 30)))
        assertFalse(ServiceTime.isAmbiguousLocalTime(date, LocalTime(4, 30)))

        // The earlier, summer-time occurrence is the one a traveller must be at
        // the bay for.
        val resolved = ServiceTime.instantAt(date, LocalTime(3, 30))
        assertEquals("2026-10-25T03:30:00+03:00", ServiceTime.formatIsoWithAthensOffset(resolved))
    }

    @Test
    fun autumnClockChangeDayIsTwentyFiveHoursLong() {
        assertEquals(25 * 60, ServiceTime.serviceDayLengthMinutes("2026-10-25"))
    }

    @Test
    fun journeyThroughAutumnOverlapIsNotAnHourTooShort() {
        // 02:45 EEST to 05:00 EET: 2 hours 15 minutes on the clock, but
        // 3 hours 15 minutes of real time.
        val departure = ServiceTime.instantAtExtendedTime("2026-10-25", 2, 45)
        val arrival = ServiceTime.instantAtExtendedTime("2026-10-25", 5, 0)
        assertEquals("2026-10-25T02:45:00+03:00", ServiceTime.formatIsoWithAthensOffset(departure))
        assertEquals("2026-10-25T05:00:00+02:00", ServiceTime.formatIsoWithAthensOffset(arrival))
        assertEquals(195, ServiceTime.durationMinutes(departure, arrival))
    }

    @Test
    fun overnightJourneyAcrossTheAutumnChangeGainsAnHour() {
        // Departs 23:30 on 2026-10-24 EEST, timetabled to arrive at 01:40,
        // which on that night is 01:40 EEST, before the change at 04:00.
        val departure = ServiceTime.instantAtExtendedTime("2026-10-24", 23, 30)
        val arrival = ServiceTime.instantAtExtendedTime("2026-10-24", 25, 40)
        assertEquals("2026-10-24", ServiceTime.serviceDateOfDeparture(departure))
        assertTrue(ServiceTime.crossesMidnight(departure, arrival))
        assertEquals(130, ServiceTime.durationMinutes(departure, arrival))

        // The night of the change itself: departs 23:30 on 2026-10-25, which is
        // already EET, so nothing shifts.
        val changeNightDeparture = ServiceTime.instantAtExtendedTime("2026-10-25", 23, 30)
        assertEquals(
            "2026-10-25T23:30:00+02:00",
            ServiceTime.formatIsoWithAthensOffset(changeNightDeparture),
        )
    }

    @Test
    fun serviceDayWindowMatchesTheZoneNotAConstant() {
        val (start, end) = ServiceTime.serviceDayWindow("2026-10-02")
        assertEquals("2026-10-02T00:00:00+03:00", ServiceTime.formatIsoWithAthensOffset(start))
        assertEquals("2026-10-03T00:00:00+03:00", ServiceTime.formatIsoWithAthensOffset(end))
        assertEquals(24 * 60, ServiceTime.durationMinutes(start, end))
    }

    @Test
    fun extendedTimesRollIntoTheNextCalendarDay() {
        val instant = ServiceTime.instantAtExtendedTime("2026-10-02", 25, 40)
        assertEquals("2026-10-03T01:40:00+03:00", ServiceTime.formatIsoWithAthensOffset(instant))
        // The service date is still the earlier one, by definition.
        assertEquals("2026-10-03", ServiceTime.localDateTimeOf(instant).date.toString())
    }

    @Test
    fun shiftServiceDateWorksBothWays() {
        assertEquals("2026-10-03", ServiceTime.shiftServiceDate("2026-10-02", 1))
        assertEquals("2026-10-01", ServiceTime.shiftServiceDate("2026-10-02", -1))
        assertEquals("2026-11-01", ServiceTime.shiftServiceDate("2026-10-31", 1))
        assertEquals("2027-01-01", ServiceTime.shiftServiceDate("2026-12-31", 1))
    }

    @Test
    fun freshnessBucketsAreExact() {
        val checked = "2026-09-29T18:30:00Z"

        val fresh = ServiceTime.freshness(checked, "2026-09-30T10:00:00Z")
        assertEquals(FreshnessState.FRESH, fresh.state)
        assertEquals(15, fresh.ageHours)

        val boundary = ServiceTime.freshness(checked, "2026-09-30T18:30:00Z")
        assertEquals(FreshnessState.FRESH, boundary.state)
        assertEquals(24, boundary.ageHours)

        val aging = ServiceTime.freshness(checked, "2026-09-30T19:30:00Z")
        assertEquals(FreshnessState.AGING, aging.state)
        assertEquals(25, aging.ageHours)

        val agingLimit = ServiceTime.freshness(checked, "2026-10-06T18:30:00Z")
        assertEquals(FreshnessState.AGING, agingLimit.state)

        val stale = ServiceTime.freshness(checked, "2026-10-06T19:30:00Z")
        assertEquals(FreshnessState.STALE, stale.state)
    }

    @Test
    fun freshnessOfAFutureTimestampIsTreatedAsZeroAge() {
        val result = ServiceTime.freshness("2026-10-05T00:00:00Z", "2026-10-01T00:00:00Z")
        assertEquals(FreshnessState.FRESH, result.state)
        assertEquals(0, result.ageHours)
    }

    @Test
    fun unparseableTimestampsAreStaleRatherThanFresh() {
        val result = ServiceTime.freshness("not a timestamp", "2026-10-01T00:00:00Z")
        assertEquals(FreshnessState.STALE, result.state)
    }

    @Test
    fun malformedServiceDatesAreRejected() {
        assertEquals(null, ServiceTime.parseServiceDateOrNull("2026-10"))
        assertEquals(null, ServiceTime.parseServiceDateOrNull("02/10/2026"))
        assertEquals(null, ServiceTime.parseServiceDateOrNull(null))
        assertEquals(null, ServiceTime.parseServiceDateOrNull("2026-13-01"))
        assertEquals(LocalDate.parse("2026-10-02"), ServiceTime.parseServiceDateOrNull("2026-10-02"))
    }
}
