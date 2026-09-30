package dev.peterdsp.poravia

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput

/**
 * Matches a test tag by prefix.
 *
 * Identifiers in this product are opaque and content-addressed, so a test that
 * hard-codes one would break the next time the demonstration release is
 * regenerated. Matching "the first journey card" rather than "journey
 * kt_0c7bc…" keeps the tests about behaviour.
 */
fun hasTestTagPrefix(prefix: String): SemanticsMatcher = SemanticsMatcher(
    "TestTag starts with '" + prefix + "'",
) { node ->
    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
}

fun SemanticsNodeInteractionsProvider.firstWithTagPrefix(prefix: String) =
    onAllNodes(hasTestTagPrefix(prefix))[0]

/** True when at least one node carries the tag. */
fun ComposeTestRule.tagExists(tag: String): Boolean =
    onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

fun ComposeTestRule.countWithTagPrefix(prefix: String): Int =
    onAllNodes(hasTestTagPrefix(prefix)).fetchSemanticsNodes().size

/**
 * Waits for a tag to appear. The emulator this suite runs on is slow enough
 * that a fixed sleep would be either flaky or wasteful.
 */
fun ComposeTestRule.awaitTag(tag: String, timeoutMillis: Long = 30_000) {
    waitUntil(timeoutMillis) { tagExists(tag) }
}

fun ComposeTestRule.awaitTagPrefix(prefix: String, timeoutMillis: Long = 30_000) {
    waitUntil(timeoutMillis) { countWithTagPrefix(prefix) > 0 }
}

/**
 * Gets past first launch when it is showing.
 *
 * First launch is a real screen backed by real persisted state, so it appears
 * once per installation and every test has to cope with both cases rather than
 * depending on the order the suite happens to run in.
 *
 * The wait matters as much as the click. The application reads its settings
 * from disk and seeds the bundled data release before it can draw anything, and
 * on a cold emulator that takes seconds. Checking for the welcome button
 * immediately would find nothing, skip the click, and then fail every later
 * assertion for a reason that has nothing to do with the code under test.
 */
fun ComposeTestRule.completeFirstRunIfShown() {
    waitUntil(BOOT_TIMEOUT_MILLIS) {
        tagExists("welcome-start") || tagExists("search-origin") || tagExists("tab-search")
    }
    if (tagExists("welcome-start")) {
        onNodeWithTag("welcome-start").performClick()
        waitUntil(BOOT_TIMEOUT_MILLIS) { tagExists("search-origin") || tagExists("tab-search") }
    }
    waitForIdle()
}

private const val BOOT_TIMEOUT_MILLIS = 60_000L

/** Chooses both endpoints of the demonstration corridor and returns to search. */
fun ComposeTestRule.chooseCorridor() {
    awaitTag("search-origin")
    onNodeWithTag("search-origin").performClick()
    awaitTag("place-query")
    onNodeWithTag("place-query").performTextInput("Aloria")
    awaitTagPrefix("place-")
    // The terminal comes first in the group, which is the disambiguation the
    // picker exists to show.
    firstWithTagPrefix("place-k").performClick()

    awaitTag("search-destination")
    onNodeWithTag("search-destination").performClick()
    awaitTag("place-query")
    onNodeWithTag("place-query").performTextInput("Oravo")
    awaitTagPrefix("place-k")
    firstWithTagPrefix("place-k").performClick()

    awaitTag("search-run")
    onNodeWithTag("search-run").assertIsDisplayed()
}
