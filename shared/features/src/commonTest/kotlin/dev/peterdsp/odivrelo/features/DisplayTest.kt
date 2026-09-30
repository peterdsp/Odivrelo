package dev.peterdsp.odivrelo.features

import dev.peterdsp.odivrelo.core.model.BoardingRule
import dev.peterdsp.odivrelo.core.model.Freshness
import dev.peterdsp.odivrelo.core.model.FreshnessState
import dev.peterdsp.odivrelo.features.format.ByteUnit
import dev.peterdsp.odivrelo.features.format.Display
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

/**
 * The presentation decisions both platforms share.
 *
 * These are the values that would eventually be derived differently on each
 * platform if they were left to the interfaces, which is how two screens of the
 * same product end up disagreeing about when a coach leaves.
 */
class DisplayTest {

    @Test
    fun aClockReadingIsInAthensTimeWhateverTheDeviceIsSetTo() {
        val summer = Display.reading("2026-10-02T09:00:00+03:00")
        assertNotNull(summer)
        assertEquals(9, summer.hour)
        assertEquals(0, summer.minute)
        assertEquals("+03:00", summer.offsetLabel)

        // The same instant written in UTC must read as the same Athens clock time.
        val sameInstantInUtc = Display.reading("2026-10-02T06:00:00Z")
        assertNotNull(sameInstantInUtc)
        assertEquals(summer.hour, sameInstantInUtc.hour)
        assertEquals(summer.minute, sameInstantInUtc.minute)
        assertEquals(summer.date, sameInstantInUtc.date)
    }

    @Test
    fun winterReadingsCarryTheWinterOffset() {
        val winter = Display.reading("2026-11-10T09:00:00+02:00")
        assertNotNull(winter)
        assertEquals("+02:00", winter.offsetLabel)
        assertEquals(9, winter.hour)
    }

    @Test
    fun anArrivalAfterMidnightIsMarkedAsTheNextDay() {
        val serviceDate = "2026-10-02"
        val departure = Display.reading("2026-10-02T23:40:00+03:00", serviceDate)
        val arrival = Display.reading("2026-10-03T01:20:00+03:00", serviceDate)
        assertNotNull(departure)
        assertNotNull(arrival)

        assertFalse(departure.isNextDay)
        assertTrue(arrival.isNextDay, "an arrival past midnight needs a next-day marker")
        assertEquals(1, arrival.hour)
        assertEquals(20, arrival.minute)
        assertEquals(80, arrival.minutesOfDay)
    }

    @Test
    fun aReadingWithoutAServiceDateNeverClaimsToBeTheNextDay() {
        val reading = Display.reading("2026-10-03T01:20:00+03:00")
        assertNotNull(reading)
        assertFalse(
            reading.isNextDay,
            "with no service date to compare against there is nothing to claim",
        )
    }

    @Test
    fun anUnparseableTimestampProducesNoReadingRatherThanAWrongOne() {
        assertNull(Display.reading("not a timestamp"))
        assertNull(Display.reading(""))
        assertNull(Display.reading("2026-10-02"))
    }

    @Test
    fun durationsSplitIntoHoursAndMinutes() {
        assertEquals(3 to 10, Display.duration(190).let { it.hours to it.minutes })
        assertEquals(0 to 45, Display.duration(45).let { it.hours to it.minutes })
        assertEquals(1 to 0, Display.duration(60).let { it.hours to it.minutes })
        assertFalse(Display.duration(45).hasHours)
        assertTrue(Display.duration(60).hasHours)
        assertEquals(190, Display.duration(190).totalMinutes)
        // A negative duration is a data problem, not something to render as -1h.
        assertEquals(0, Display.duration(-5).totalMinutes)
    }

    @Test
    fun freshnessPrefersDaysOnceHoursStopBeingReadable() {
        val hours = Display.freshness(Freshness("2026-09-30T00:00:00Z", 6, FreshnessState.FRESH))
        assertEquals(FreshnessState.FRESH, hours.state)
        assertEquals(6, hours.ageHours)
        assertFalse(hours.preferDays)
        assertFalse(hours.isRecent)

        val days = Display.freshness(Freshness("2026-09-01T00:00:00Z", 236, FreshnessState.STALE))
        assertEquals(FreshnessState.STALE, days.state)
        assertEquals(9, days.ageDays)
        assertTrue(days.preferDays)

        val justNow = Display.freshness(Freshness("2026-09-30T00:00:00Z", 0, FreshnessState.FRESH))
        assertTrue(justNow.isRecent)

        // A negative age is a clock disagreement, not a fact from the future.
        assertEquals(0, Display.freshness(Freshness("x", -4, FreshnessState.FRESH)).ageHours)
    }

    @Test
    fun aPastServiceDateIsRecognisedSoAScreenCanSaySoInsteadOfShowingNothing() {
        val now = Instant.parse("2026-10-02T09:00:00+03:00")
        assertTrue(Display.isPast("2026-10-01", now))
        assertFalse(Display.isPast("2026-10-02", now))
        assertFalse(Display.isPast("2026-10-03", now))
        assertTrue(Display.isToday("2026-10-02", now))
        assertFalse(Display.isToday("2026-10-03", now))
        assertFalse(Display.isPast("not a date", now))
    }

    @Test
    fun theDateWindowIsCentredOnTodayInAthens() {
        // Just before midnight in Athens is already the next day in UTC terms for
        // some offsets, so the window has to be built from the Athens date.
        val now = Instant.parse("2026-10-02T23:30:00+03:00")
        val window = Display.dateWindow(now, daysBefore = 1, daysAfter = 2)
        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04"), window)
    }

    @Test
    fun boardingRulesReadAsAllowedUnlessTheyAreForbidden() {
        assertTrue(Display.canBoard(BoardingRule.ALLOWED))
        assertTrue(Display.canBoard(BoardingRule.ON_REQUEST))
        assertTrue(Display.canBoard(BoardingRule.COORDINATE_WITH_OPERATOR))
        assertFalse(Display.canBoard(BoardingRule.NOT_ALLOWED))

        assertTrue(Display.canAlight(BoardingRule.ON_REQUEST))
        assertFalse(Display.canAlight(BoardingRule.NOT_ALLOWED))
    }

    @Test
    fun byteSizesPickAUnitAndADecimalPlace() {
        assertEquals(ByteUnit.BYTES, Display.bytes(512).unit)
        assertEquals(0, Display.bytes(512).decimals)

        val kilobytes = Display.bytes(15_883)
        assertEquals(ByteUnit.KILOBYTES, kilobytes.unit)
        assertEquals(1, kilobytes.decimals)
        assertTrue(kilobytes.value > 15.0 && kilobytes.value < 16.0)

        assertEquals(ByteUnit.MEGABYTES, Display.bytes(5L * 1024 * 1024).unit)
        assertEquals(ByteUnit.GIGABYTES, Display.bytes(3L * 1024 * 1024 * 1024).unit)
        assertEquals(ByteUnit.BYTES, Display.bytes(0).unit)
    }
}
