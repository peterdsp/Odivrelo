package dev.peterdsp.odivrelo.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Objective-C export contract, asserted over the real source.
 *
 * ## Why this test exists
 *
 * On Kotlin/Native an exception that is not listed in a function's `@Throws` is
 * not converted into an `NSError`. The runtime terminates the process instead,
 * before Swift can catch anything. A fresh installation with no data release
 * therefore killed the iOS application on its first call to `meta()`, while 158
 * tests passed, because the annotation was missing on all eighteen exported
 * suspending members.
 *
 * ## Why it reads the source rather than the header
 *
 * The generated header cannot show this. A suspending function is exported as
 * `…WithCompletionHandler:(void (^)(T *, NSError *))`, and that `NSError`
 * parameter is present whether or not the function declares `@Throws`. The
 * header is byte-identical either way. What the annotation changes is only
 * whether the runtime fills that parameter in or terminates. So the fact has to
 * come from the declaration, which the `generateExportedApiFacts` Gradle task
 * parses out of OdivreloCore.kt and OdivreloCoreExtras.kt.
 *
 * [FirstRunTest] covers the other half: that nothing but a [OdivreloException]
 * ever reaches the boundary in the first place.
 */
class ExportedApiContractTest {

    private val requiredThrows = setOf("OdivreloException", "CancellationException")

    @Test
    fun theParseFoundTheWholeExportedSurface() {
        // A parser that silently matched nothing would make every other assertion
        // in this file vacuously true.
        val suspending = ExportedApiFacts.members.count { it.isSuspend }
        assertTrue(
            suspending >= ExportedApiFacts.MINIMUM_SUSPEND_MEMBERS,
            "expected at least ${ExportedApiFacts.MINIMUM_SUSPEND_MEMBERS} suspending " +
                "exported members, parsed $suspending",
        )
    }

    @Test
    fun everyMemberTheProductAgreedOnIsStillExported() {
        // The shape both applications were written against. Losing one of these
        // is a breaking change and should fail here, not in an Xcode build.
        val expected = listOf(
            "meta", "coverage", "searchPlaces", "searchJourneys", "journeyDetail",
            "operatorDetail", "stopDetail", "sources", "savedTrips", "saveTrip",
            "removeSavedTrip", "favorites", "toggleFavorite", "recentSearches",
            "offlineCatalog", "installedPacks", "removePack", "rollbackToPreviousRelease",
        )
        val found = ExportedApiFacts.members
            .filter { it.owner == "OdivreloCore" && it.isSuspend }
            .map { it.name }
        expected.forEach {
            assertTrue(it in found, "OdivreloCore no longer exports a suspending '$it'")
        }
        assertEquals(
            expected.size,
            found.size,
            "OdivreloCore's suspending members changed: expected $expected, found $found",
        )

        val nonSuspending = ExportedApiFacts.members
            .filter { it.owner == "OdivreloCore" && !it.isSuspend }
            .map { it.name }
        listOf("downloadPack", "freshnessOf", "close").forEach {
            assertTrue(it in nonSuspending, "OdivreloCore no longer exports '$it'")
        }
    }

    @Test
    fun everyExportedSuspendingMemberDeclaresThrows() {
        val offenders = ExportedApiFacts.members
            .filter { it.isSuspend }
            .filterNot { it.declaredThrows.toSet().containsAll(requiredThrows) }

        assertTrue(
            offenders.isEmpty(),
            "These exported suspending members are missing " +
                "@Throws(OdivreloException::class, CancellationException::class). " +
                "Without it, Kotlin/Native terminates the process instead of " +
                "handing Swift an NSError:\n" +
                offenders.joinToString("\n") { "  " + it.owner + "." + it.name },
        )
    }

    @Test
    fun theExtrasInterfaceIsHeldToTheSameRule() {
        val extras = ExportedApiFacts.members.filter { it.owner == "OdivreloCoreExtras" }
        assertEquals(2, extras.size, "OdivreloCoreExtras changed: $extras")
        extras.forEach {
            assertTrue(it.isSuspend)
            assertTrue(
                it.declaredThrows.toSet().containsAll(requiredThrows),
                "OdivreloCoreExtras." + it.name + " is missing @Throws",
            )
        }
    }

    @Test
    fun theFactoryDeclaresThrowsSoABadConfigurationDoesNotTerminateTheProcess() {
        val factory = ExportedApiFacts.members.single { it.name == "createOdivreloCore" }
        assertFalse(factory.isSuspend)
        assertTrue(
            "OdivreloException" in factory.declaredThrows,
            "createOdivreloCore must declare @Throws: it is the first thing a host " +
                "application calls, and an unwritable path or an unopenable " +
                "database would otherwise terminate the process on launch.",
        )
    }

    @Test
    fun aMemberThatCannotThrowIsNotRequiredToDeclareThrows() {
        // close() and freshnessOf() do no I/O and no decoding. Annotating them
        // would be noise, so the rule is deliberately scoped to members that can
        // actually fail rather than applied to everything.
        val close = ExportedApiFacts.members.single { it.owner == "OdivreloCore" && it.name == "close" }
        assertFalse(close.isSuspend)
        assertTrue(close.declaredThrows.isEmpty())
    }
}
