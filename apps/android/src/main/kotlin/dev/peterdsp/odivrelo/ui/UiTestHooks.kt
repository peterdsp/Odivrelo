package dev.peterdsp.odivrelo.ui

/**
 * Deterministic UI seams for instrumentation tests, in the spirit of
 * [dev.peterdsp.odivrelo.core.time.ServiceClock].
 *
 * Production never sets these, so behaviour is unchanged. An instrumentation test
 * can force reduced motion on, which makes every indeterminate progress indicator
 * render its static variant. A static indicator requests no further frames, so
 * the Compose UI-test clock can reach idle rather than waiting forever on a
 * perpetual animation, which is what otherwise hangs `waitUntil` and activity
 * teardown. This does not depend on the emulator's animation-scale setting being
 * read correctly in-process.
 */
object UiTestHooks {
    @Volatile
    var forceReduceMotion: Boolean = false
}
