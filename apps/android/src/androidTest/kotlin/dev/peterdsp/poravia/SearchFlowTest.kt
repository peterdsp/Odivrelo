package dev.peterdsp.poravia

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The critical flow, end to end, against the release the application bundles.
 *
 * The demonstration release materialises journeys packs for a handful of dates
 * only. Today is deliberately not one of them, which makes this suite the place
 * the product's central honesty rule is actually exercised rather than asserted
 * against a fixture.
 */
@RunWith(AndroidJUnit4::class)
class SearchFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int): String = rule.activity.getString(id)

    @Test
    fun a_first_launch_asks_for_nothing_and_leads_to_search() {
        rule.waitForIdle()
        if (rule.tagExists("welcome-start")) {
            // No account field, no permission dialog, no network-only step.
            rule.onNodeWithTag("welcome-title").assertIsDisplayed()
            rule.onNodeWithTag("welcome-language-el").assertIsDisplayed()
            rule.onNodeWithTag("welcome-language-en").assertIsDisplayed()
            rule.onNodeWithTag("welcome-language-sq").assertIsDisplayed()
            rule.onNodeWithTag("welcome-start").performClick()
        }
        rule.awaitTag("search-origin")
        rule.onNodeWithTag("search-run").assertIsDisplayed()
    }

    @Test
    fun b_a_date_with_no_pack_says_so_and_never_claims_there_is_no_service() {
        rule.completeFirstRunIfShown()
        rule.chooseCorridor()

        // Today's service date. The bundled release names no journeys pack for
        // it, which is a statement about this device and not about the world.
        rule.onNodeWithTag("search-today").performClick()
        rule.onNodeWithTag("search-run").performClick()

        rule.awaitTag("results-no-offline-pack")
        rule.onNodeWithTag("results-no-offline-pack").assertIsDisplayed()

        // The sentence that must never appear here.
        val noService = string(R.string.results_empty_no_service_title)
        assertEquals(
            "a date with no installed pack must not be reported as no service",
            0,
            rule.onAllNodesWithText(noService, substring = true).fetchSemanticsNodes().size,
        )

        // And the sentence that must.
        rule.onAllNodesWithText(string(R.string.results_empty_no_offline_title), substring = true)
            .fetchSemanticsNodes()
            .let { assertTrue("the no-offline-data title is missing", it.isNotEmpty()) }
    }

    @Test
    fun c_a_date_with_a_pack_returns_journeys_with_every_claim_labelled() {
        rule.completeFirstRunIfShown()
        rule.chooseCorridor()

        // 2026-09-30 plus two days is 2026-10-02, a date the release names.
        rule.onNodeWithTag("search-today").performClick()
        rule.onNodeWithTag("search-next-day").performClick()
        rule.onNodeWithTag("search-next-day").performClick()
        rule.onNodeWithTag("search-run").performClick()

        rule.awaitTag("results-list")
        rule.awaitTagPrefix("journey-")
        assertTrue("no journey card rendered", rule.countWithTagPrefix("journey-") > 0)

        // Scheduled is the only quality this release publishes, and it says so.
        assertTrue(
            "the schedule-only statement is missing",
            rule.onAllNodesWithText(string(R.string.live_unavailable_title), substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun d_a_journey_shows_its_boarding_point_provenance_and_the_no_ticket_statement() {
        rule.completeFirstRunIfShown()
        rule.chooseCorridor()
        rule.onNodeWithTag("search-today").performClick()
        rule.onNodeWithTag("search-next-day").performClick()
        rule.onNodeWithTag("search-next-day").performClick()
        rule.onNodeWithTag("search-run").performClick()

        rule.awaitTagPrefix("journey-")
        rule.firstWithTagPrefix("journey-").performClick()

        rule.awaitTag("journey-boarding-point")
        rule.onNodeWithTag("journey-boarding-point").assertIsDisplayed()

        // Poravia sells nothing, and says so on the purchase surface itself.
        rule.onNodeWithTag("booking-disclaimer").performScrollTo().assertIsDisplayed()
        assertTrue(
            "the no-ticket statement is missing from the purchase surface",
            rule.onAllNodesWithText(string(R.string.no_tickets_sold), substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )

        // Saving a trip is what makes it readable at the bay with no signal.
        rule.onNodeWithTag("journey-save").performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test
    fun e_the_demonstration_notice_is_present_and_cannot_be_dismissed() {
        rule.completeFirstRunIfShown()
        rule.awaitTag("search-origin")

        val notice = string(R.string.demo_notice_title)
        assertTrue(
            "the demonstration notice is missing from search",
            rule.onAllNodesWithText(notice, substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
        // There is no close affordance anywhere on it, by design.
        assertEquals(
            0,
            rule.onAllNodesWithText(string(R.string.action_close))
                .fetchSemanticsNodes()
                .size,
        )
    }
}
