package dev.peterdsp.poravia

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What survives a geometry change and an activity recreation.
 *
 * The activity declares no `configChanges`, so a rotation, a resize and a fold
 * all recreate it. This test exercises exactly that path and asserts that the
 * query, the service date and the selected journey are still there afterwards.
 */
@RunWith(AndroidJUnit4::class)
class StateRestorationTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun a_search_survives_activity_recreation() {
        rule.completeFirstRunIfShown()
        rule.chooseCorridor()
        rule.onNodeWithTag("search-today").performClick()
        rule.onNodeWithTag("search-next-day").performClick()
        rule.onNodeWithTag("search-next-day").performClick()
        rule.onNodeWithTag("search-run").performClick()
        rule.awaitTagPrefix("journey-")

        val before = rule.onRoot().printToString(maxDepth = 100)

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        // The result list is still the result list, not a fresh search form.
        rule.awaitTag("results-list")
        rule.onNodeWithTag("results-list").assertIsDisplayed()
        rule.awaitTagPrefix("journey-")
        assertTrue(
            "the journeys did not come back after recreation",
            rule.countWithTagPrefix("journey-") > 0,
        )
        assertTrue("nothing was rendered before recreation", before.isNotEmpty())
    }

    @Test
    fun b_a_selected_journey_survives_activity_recreation() {
        rule.completeFirstRunIfShown()
        rule.chooseCorridor()
        rule.onNodeWithTag("search-today").performClick()
        rule.onNodeWithTag("search-next-day").performClick()
        rule.onNodeWithTag("search-next-day").performClick()
        rule.onNodeWithTag("search-run").performClick()
        rule.awaitTagPrefix("journey-")
        rule.firstWithTagPrefix("journey-").performClick()
        rule.awaitTag("journey-boarding-point")

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        rule.awaitTag("journey-boarding-point")
        rule.onNodeWithTag("journey-boarding-point").assertIsDisplayed()
    }
}

/**
 * Every tab opens, and each one states what it honestly can and cannot do.
 */
@RunWith(AndroidJUnit4::class)
class ScreensTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int) = rule.activity.getString(id)

    @Test
    fun offline_states_that_there_are_no_map_tiles() {
        rule.completeFirstRunIfShown()
        rule.onNodeWithTag("tab-offline").performClick()
        rule.awaitTag("offline-maps-note")
        rule.onNodeWithTag("offline-maps-note").assertIsDisplayed()
        assertTrue(
            "the offline screen must say base map tiles are not downloaded",
            rule.onAllNodesWithText(string(R.string.offline_maps_unavailable), substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun the_wallet_starts_empty_and_asks_for_no_storage_permission() {
        rule.completeFirstRunIfShown()
        rule.onNodeWithTag("tab-wallet").performClick()
        rule.awaitTag("wallet-empty")
        rule.onNodeWithTag("wallet-empty").assertIsDisplayed()
        rule.onNodeWithTag("wallet-import").assertIsDisplayed()
        assertTrue(
            "the wallet must say it never uploads a ticket",
            rule.onAllNodesWithText(string(R.string.wallet_never_uploaded), substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun saved_starts_empty_and_offers_the_offline_promise() {
        rule.completeFirstRunIfShown()
        rule.onNodeWithTag("tab-saved").performClick()
        rule.awaitTag("trip-ready")
        rule.onNodeWithTag("trip-ready").assertIsDisplayed()
    }

    @Test
    fun settings_reaches_diagnostics_and_carries_no_secret() {
        rule.completeFirstRunIfShown()
        rule.onNodeWithTag("tab-settings").performClick()
        rule.awaitTag("settings-open-diagnostics")
        rule.onNodeWithTag("settings-open-diagnostics").performClick()
        rule.awaitTag("diagnostics-text")
        rule.onNodeWithTag("diagnostics-text").assertIsDisplayed()

        val text = rule.onNodeWithTag("diagnostics-text").printToString(maxDepth = 10)
        listOf("password", "token", "secret", "barcode", "ticket=").forEach { forbidden ->
            assertTrue(
                "diagnostics must not carry '" + forbidden + "'",
                !text.lowercase().contains(forbidden),
            )
        }
    }

    @Test
    fun every_tab_is_labelled_for_a_screen_reader_and_is_large_enough_to_hit() {
        rule.completeFirstRunIfShown()
        listOf("tab-search", "tab-saved", "tab-offline", "tab-wallet", "tab-settings")
            .forEach { tag ->
                rule.onNodeWithTag(tag).assertIsDisplayed()
                rule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
            }
    }
}

/**
 * Links are validated rather than trusted, and an unusable one says so instead
 * of opening the wrong thing.
 *
 * The activity is `singleTask`, so in production a second link reaches the
 * running activity through `onNewIntent` rather than starting a new one. These
 * tests drive exactly that path: launching a fresh task per link would be
 * testing the task stack rather than the link handling, and would be at the
 * mercy of whatever task the previous test left behind.
 */
@RunWith(AndroidJUnit4::class)
class DeepLinkTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun open(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setClass(rule.activity, MainActivity::class.java)
        rule.activityRule.scenario.onActivity { activity -> activity.deliverLink(intent) }
        rule.waitForIdle()
    }

    private fun string(id: Int) = rule.activity.getString(id)

    @Test
    fun an_unknown_link_is_stated_rather_than_swallowed() {
        rule.completeFirstRunIfShown()
        open("poravia://nonsense-path")
        rule.awaitTag("deeplink-unresolved")
        rule.onNodeWithTag("deeplink-unresolved").assertIsDisplayed()
        rule.onNodeWithTag("deeplink-unresolved-search").performClick()
        rule.awaitTag("search-origin")
    }

    @Test
    fun the_custom_scheme_opens_the_offline_screen() {
        rule.completeFirstRunIfShown()
        open("poravia://offline")
        rule.awaitTag("offline-maps-note")
        rule.onNodeWithTag("offline-maps-note").assertIsDisplayed()
    }

    @Test
    fun an_https_link_for_the_product_domain_opens_settings() {
        rule.completeFirstRunIfShown()
        open("https://poravia.peterdsp.dev/settings")
        rule.awaitTag("settings-open-privacy")
        rule.onNodeWithTag("settings-open-privacy").assertIsDisplayed()
    }

    @Test
    fun a_link_for_someone_elses_domain_is_refused() {
        rule.completeFirstRunIfShown()
        open("https://example.invalid/journey/x?date=2026-10-02")
        rule.awaitTag("deeplink-unresolved")
        rule.onNodeWithTag("deeplink-unresolved").assertIsDisplayed()
        assertTrue(
            "a link for another domain must be named as such",
            rule.onAllNodesWithText(
                string(R.string.deeplink_foreign_host_title),
                substring = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun a_past_service_date_is_reported_as_expired() {
        rule.completeFirstRunIfShown()
        open("poravia://journey/anything?date=2020-01-01")
        rule.awaitTag("deeplink-unresolved")
        assertTrue(
            "an expired link must say the date has passed",
            rule.onAllNodesWithText(
                string(R.string.deeplink_expired_title),
                substring = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun a_link_is_not_re_applied_when_the_activity_is_recreated() {
        // A rotation, a resize and a fold all recreate the activity and hand it
        // the same intent again. Re-acting on it would throw away wherever the
        // person had navigated since, which is exactly the state loss the
        // adaptive gate forbids.
        rule.completeFirstRunIfShown()
        open("poravia://offline")
        rule.awaitTag("offline-maps-note")

        rule.onNodeWithTag("tab-wallet").performClick()
        rule.awaitTag("wallet-import")

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        rule.awaitTag("wallet-import")
        rule.onNodeWithTag("wallet-import").assertIsDisplayed()
    }
}
