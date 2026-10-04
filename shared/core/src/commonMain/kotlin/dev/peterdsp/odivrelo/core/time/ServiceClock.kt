package dev.peterdsp.odivrelo.core.time

import kotlin.concurrent.Volatile
import kotlin.native.ObjCName
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * The one boundary between the product and the wall clock.
 *
 * Every "what is today" and "is this fresh" decision resolves "now" through this
 * object, so production reads the real clock while an automated test can pin it
 * to a fixed instant. That is what keeps a test's outcome from changing as the
 * calendar advances: a search for "today" is deterministic because "today" is
 * computed from an instant the test controls, in `Europe/Athens` via
 * [ServiceTime.currentServiceDate].
 *
 * The override is a test seam only. Production never sets it, so [now] returns
 * [Clock.System] exactly as before.
 */
@ObjCName("OdivreloServiceClock")
object ServiceClock {
    @Volatile
    private var overrideInstant: Instant? = null

    /** The current instant, or the pinned instant when a test has set one. */
    fun now(): Instant = overrideInstant ?: Clock.System.now()

    /** Pin "now" to a fixed instant for a test, or pass null to restore the real clock. */
    fun overrideNow(instant: Instant?) {
        overrideInstant = instant
    }
}
