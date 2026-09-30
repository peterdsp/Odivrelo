package dev.peterdsp.poravia.core

import dev.peterdsp.poravia.core.model.JourneyDetail
import kotlin.coroutines.cancellation.CancellationException
import kotlin.native.ObjCName

/**
 * Host-application capabilities that sit beside the contract surface.
 *
 * They are deliberately kept off [PoraviaCore] so that interface stays exactly
 * the shape both platform applications agreed on. These two are needed by a host
 * that bundles a release with the application binary and by a Trip Ready screen
 * that must open with no network and no installed release.
 *
 * Both carry `@Throws` for the same reason every member of [PoraviaCore] does:
 * without it, a Kotlin exception crossing the Objective-C boundary terminates the
 * process instead of reaching Swift as an `NSError`.
 */
@ObjCName("PoraviaCoreExtras")
interface PoraviaCoreExtras {

    /**
     * The full cached detail of a saved trip, or null when it is not saved.
     *
     * The cached copy is what a traveller reads at the bay with no signal. It
     * holds the reviewed boarding point, the ordered stop list and the verified
     * ticket-office fallback. It never holds a ticket document.
     */
    @Throws(PoraviaException::class, CancellationException::class)
    suspend fun savedTripDetail(savedTripId: String): JourneyDetail?

    /**
     * Registers a release the host application placed in the packs directory,
     * for example one shipped inside the application. Digests are verified
     * exactly as a downloaded pack's are: a local file gets no extra trust.
     *
     * Returns true when at least one pack was registered.
     */
    @Throws(PoraviaException::class, CancellationException::class)
    suspend fun adoptSeededRelease(): Boolean
}

/** The extras of a core built by [createPoraviaCore], or null if unavailable. */
val PoraviaCore.extras: PoraviaCoreExtras?
    get() = this as? PoraviaCoreExtras
