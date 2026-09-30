package dev.peterdsp.poravia

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.peterdsp.poravia.core.model.Freshness
import dev.peterdsp.poravia.core.model.FreshnessState
import dev.peterdsp.poravia.ui.common.Formats
import dev.peterdsp.poravia.wallet.TicketKind
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A ticket's type is decided from its own leading bytes.
 *
 * The picker can be told to offer PDFs and still hand over something else, and
 * a file name is a claim rather than a fact. Nothing but the content decides.
 */
class TicketKindTest {

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { (values[it] and 0xFF).toByte() }

    @Test
    fun `a pdf is recognised`() {
        assertEquals(TicketKind.PDF, TicketKind.sniff(bytes(0x25, 0x50, 0x44, 0x46, 0x2D)))
    }

    @Test
    fun `a png is recognised`() {
        assertEquals(
            TicketKind.PNG,
            TicketKind.sniff(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)),
        )
    }

    @Test
    fun `a jpeg is recognised`() {
        assertEquals(TicketKind.JPEG, TicketKind.sniff(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
    }

    @Test
    fun `anything else is refused`() {
        assertNull(TicketKind.sniff(bytes(0x50, 0x4B, 0x03, 0x04)))
        assertNull(TicketKind.sniff(bytes(0x3C, 0x3F, 0x78, 0x6D, 0x6C)))
        assertNull(TicketKind.sniff("<script>".encodeToByteArray()))
        assertNull(TicketKind.sniff(ByteArray(0)))
        assertNull(TicketKind.sniff(bytes(0x25)))
    }

    @Test
    fun `a file whose name lies about its type is still refused`() {
        // "ticket.pdf" containing a ZIP is a ZIP.
        assertNull(TicketKind.sniff(bytes(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)))
    }

    @Test
    fun `every accepted kind declares a media type the picker can filter on`() {
        TicketKind.entries.forEach { kind ->
            assertTrue(kind.mediaType.contains("/"))
        }
    }
}

/**
 * Times are always Athens local time, whatever the device thinks.
 *
 * A traveller in Tirana reading a coach from Kithra needs the time printed on
 * the operator's timetable, not the time on their phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FormatsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a summer departure renders in EEST`() {
        // 2026-07-01T06:00:00Z is 09:00 in Athens, which is UTC+03:00 in summer.
        assertEquals("09:00", Formats.time(context, "2026-07-01T06:00:00Z"))
    }

    @Test
    fun `a winter departure renders in EET`() {
        // 2026-12-01T06:00:00Z is 08:00 in Athens, which is UTC+02:00 in winter.
        assertEquals("08:00", Formats.time(context, "2026-12-01T06:00:00Z"))
    }

    @Test
    fun `an explicit offset is respected rather than reinterpreted`() {
        assertEquals("07:00", Formats.time(context, "2026-10-02T07:00:00+03:00"))
    }

    @Test
    fun `an unparseable instant does not render a plausible-looking time`() {
        assertEquals(
            context.getString(R.string.quality_unknown),
            Formats.time(context, "not a time"),
        )
    }

    @Test
    fun `durations read as hours and minutes`() {
        // Compared against the resource rather than a literal, so the assertion
        // holds whichever of the three languages the test runner is in.
        assertEquals(
            context.getString(R.string.duration_hours_minutes, 3, 10),
            Formats.duration(context, 190),
        )
        assertEquals(
            context.getString(R.string.duration_minutes, 45),
            Formats.duration(context, 45),
        )
        // A negative duration is clamped rather than rendered as "-5 minutes".
        assertEquals(
            context.getString(R.string.duration_minutes, 0),
            Formats.duration(context, -5),
        )
    }

    @Test
    fun `byte sizes are human sized`() {
        // Whole bytes, one decimal above that: the split and the precision are
        // the shared layer's decision, not this screen's.
        assertEquals("512 B", Formats.bytes(512))
        assertEquals("2.0 kB", Formats.bytes(2048))
        assertEquals("5.0 MB", Formats.bytes(5L * 1024 * 1024))
        assertEquals("1.0 GB", Formats.bytes(1024L * 1024 * 1024))
    }

    @Test
    fun `freshness labels match the state`() {
        val fresh = Freshness("2026-09-30T06:00:00Z", 3, FreshnessState.FRESH)
        val stale = Freshness("2026-01-01T06:00:00Z", 6000, FreshnessState.STALE)
        assertEquals(
            context.getString(R.string.freshness_fresh),
            Formats.freshnessLabel(context, fresh),
        )
        assertEquals(
            context.getString(R.string.freshness_stale),
            Formats.freshnessLabel(context, stale),
        )
        assertTrue(Formats.freshnessAge(context, stale).isNotBlank())
    }

    @Test
    fun `a service date renders in the reader's language`() {
        assertNotNull(Formats.serviceDate("2026-10-02", Locale.forLanguageTag("el")))
        assertNotNull(Formats.serviceDateLong("2026-10-02", Locale.forLanguageTag("sq")))
        // A malformed date is shown as it came rather than as a guess.
        assertEquals("nonsense", Formats.serviceDate("nonsense", Locale.ENGLISH))
    }
}
