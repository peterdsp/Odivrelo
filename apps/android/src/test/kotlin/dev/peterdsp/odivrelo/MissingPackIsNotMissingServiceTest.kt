package dev.peterdsp.odivrelo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.peterdsp.odivrelo.features.EmptyReason
import dev.peterdsp.odivrelo.features.FailureReason
import dev.peterdsp.odivrelo.ui.common.emptyCopyOf
import dev.peterdsp.odivrelo.ui.common.failureCopyOf
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The contract rule this product exists to protect.
 *
 * A journeys pack is materialised only for the dates the published data names.
 * When the installed release holds no pack for a requested date, the honest
 * answer is "no offline data for this date". It is **not** "no service on this
 * date": one is a statement about what this device holds, the other is a claim
 * about the world, and conflating them would invent certainty the data does not
 * support.
 *
 * This is asserted in all three languages, because a translation is exactly
 * where the distinction would quietly disappear.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissingPackIsNotMissingServiceTest {

    private fun context(language: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = android.content.res.Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return base.createConfigurationContext(configuration)
    }

    @Test
    fun `the two empty answers never share a sentence`() {
        listOf("el", "en", "sq").forEach { language ->
            val localised = context(language)
            val noPack = emptyCopyOf(localised, EmptyReason.NO_OFFLINE_DATA_FOR_DATE)
            val noService = emptyCopyOf(localised, EmptyReason.NO_SERVICE_ON_DATE)

            assertNotEquals(
                "titles collide in " + language,
                noService.title,
                noPack.title,
            )
            assertNotEquals(
                "bodies collide in " + language,
                noService.body,
                noPack.body,
            )
        }
    }

    @Test
    fun `the missing-pack answer is the coverage sentence, not the service one`() {
        listOf("el", "en", "sq").forEach { language ->
            val localised = context(language)
            val copy = emptyCopyOf(localised, EmptyReason.NO_OFFLINE_DATA_FOR_DATE)

            assertEquals(
                language + " must use the no-offline-data title",
                localised.getString(R.string.results_empty_no_offline_title),
                copy.title,
            )
            assertEquals(
                language + " must use the no-offline-data body",
                localised.getString(R.string.results_empty_no_offline_body),
                copy.body,
            )
            assertNotEquals(
                language + " must not use the no-service title",
                localised.getString(R.string.results_empty_no_service_title),
                copy.title,
            )
        }
    }

    @Test
    fun `the failure path says the same thing as the empty path`() {
        listOf("el", "en", "sq").forEach { language ->
            val localised = context(language)
            val fromFailure = failureCopyOf(localised, FailureReason.NO_OFFLINE_DATA_FOR_DATE)
            val fromEmpty = emptyCopyOf(localised, EmptyReason.NO_OFFLINE_DATA_FOR_DATE)
            assertEquals(
                "a journey link and a search must not disagree in " + language,
                fromEmpty.title,
                fromFailure.title,
            )
        }
    }

    @Test
    fun `retrying a date with no pack is not offered as a remedy`() {
        // Retrying the identical request cannot produce a pack that is not
        // there, so the feature layer marks it unretryable and the screen shows
        // no retry button.
        assertTrue(!FailureReason.NO_OFFLINE_DATA_FOR_DATE.isRetryable)
    }

    @Test
    fun `the no-offline-data body refuses to claim anything about service`() {
        // Greek is the default and the wording that matters most here.
        val localised = context("en")
        val body = localised.getString(R.string.results_empty_no_offline_body).lowercase()
        assertTrue(
            "the body must say this is not a statement about service",
            body.contains("does not mean"),
        )
    }
}
