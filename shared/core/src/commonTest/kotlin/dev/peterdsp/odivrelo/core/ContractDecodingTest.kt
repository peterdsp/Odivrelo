package dev.peterdsp.odivrelo.core

import dev.peterdsp.odivrelo.core.model.BoardingRule
import dev.peterdsp.odivrelo.core.model.CoverageState
import dev.peterdsp.odivrelo.core.model.DataMode
import dev.peterdsp.odivrelo.core.model.GeometryConfidence
import dev.peterdsp.odivrelo.core.model.LocalizedText
import dev.peterdsp.odivrelo.core.model.Meta
import dev.peterdsp.odivrelo.core.model.PlaceKind
import dev.peterdsp.odivrelo.core.model.PlaceResults
import dev.peterdsp.odivrelo.core.model.PositionQuality
import dev.peterdsp.odivrelo.core.model.RightsStatus
import dev.peterdsp.odivrelo.core.model.SourceList
import dev.peterdsp.odivrelo.core.model.TimeQuality
import dev.peterdsp.odivrelo.core.packs.CoveragePack
import dev.peterdsp.odivrelo.core.packs.OperatorsPack
import dev.peterdsp.odivrelo.core.packs.PackNames
import dev.peterdsp.odivrelo.core.packs.StopsPack
import dev.peterdsp.odivrelo.core.serialization.OdivreloJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Decoding of contract version 1, against the one canonical published release.
 *
 * These tests read the release rather than a fixture copied beside them, so a
 * change to what the publisher emits fails here instead of silently passing.
 */
class ContractDecodingTest {

    @Test
    fun manifestDeclaresEveryRequiredPackWithADigestAndLength() {
        val manifest = Release.manifest
        assertEquals("1.0.0", manifest.contractVersion)
        assertEquals("Odivrelo", manifest.product)
        assertTrue(manifest.releaseId.isNotBlank())
        assertTrue(manifest.publishedAt.isNotBlank())

        PackNames.required.forEach { name ->
            val file = manifest.files[name]
            assertNotNull(file, "manifest is missing required pack '$name'")
            assertEquals(64, file.sha256.length, "pack '$name' has no full SHA-256")
            assertTrue(file.bytes > 0, "pack '$name' declares no length")
            assertTrue(
                file.path.startsWith("packs/"),
                "pack '$name' is not served from the packs directory",
            )
            assertTrue(
                file.path.contains(file.sha256.take(16)),
                "pack '$name' file name is not content addressed",
            )
        }
    }

    @Test
    fun theReleasePublishesGtfsAndAtLeastOneServiceDate() {
        val gtfs = Release.manifest.files[PackNames.GTFS]
        assertNotNull(gtfs, "a release must publish its GTFS archive")
        assertEquals("application/zip", gtfs.mediaType)
        assertTrue(gtfs.path.endsWith(".zip"))

        assertTrue(Release.serviceDates.isNotEmpty(), "a release must publish some service date")
        Release.serviceDates.forEach { date ->
            assertEquals(10, date.length, "service date '$date' is not YYYY-MM-DD")
            assertNotNull(ServiceDateProbe.parse(date), "service date '$date' does not parse")
        }
    }

    @Test
    fun theGtfsArchiveIsARealZipAndIsNeverDecodedAsJson() {
        val bytes = Release.packBytes(PackNames.GTFS)
        assertTrue(bytes.size > 4)
        // PK: the local file header of a zip.
        assertEquals(0x50, bytes[0].toInt() and 0xFF)
        assertEquals(0x4B, bytes[1].toInt() and 0xFF)
        // The core does not list GTFS as required, so it never tries to decode it.
        assertFalse(PackNames.GTFS in PackNames.required)
    }

    @Test
    fun metaPackDecodesDirectlyAsTheMetaEndpointResponse() {
        val meta = OdivreloJson.instance.decodeFromString(
            Meta.serializer(),
            Release.packText(PackNames.META),
        )
        assertEquals("1.0.0", meta.contractVersion)
        assertEquals(Release.releaseId, meta.releaseId)
        assertEquals(DataMode.DEMO, meta.dataMode)
        assertTrue(meta.isDemo, "the beta release must declare itself as demonstration data")
        assertEquals("Odivrelo", meta.product.name)
        assertEquals(listOf("el", "en", "sq"), meta.languages)
        assertEquals(CoverageState.DEMO, meta.coverage.state)
        assertTrue(meta.attribution.isNotEmpty(), "a release must attribute its sources")
        assertNotNull(meta.offlineManifestUrl)
    }

    @Test
    fun coveragePackStatesWhatIsNotCoveredAndWhatAbsenceMeans() {
        val pack = OdivreloJson.instance.decodeFromString(
            CoveragePack.serializer(),
            Release.packText(PackNames.COVERAGE),
        )
        assertEquals(Release.releaseId, pack.releaseId)
        val coverage = pack.coverage
        assertEquals(CoverageState.DEMO, coverage.state)
        assertTrue(
            coverage.notCovered.isNotEmpty(),
            "a release must say what it does not cover",
        )
        // The statement is localised, because it is a sentence a traveller reads
        // and this product ships in three languages.
        coverage.notCovered.forEach { statement ->
            listOf("el", "en", "sq").forEach { language ->
                assertTrue(
                    statement.resolve(language).isNotBlank(),
                    "a not-covered statement has no " + language + " text",
                )
            }
        }
        assertTrue(
            coverage.notCovered.any {
                it.resolve("en").contains("real-time", ignoreCase = true)
            },
            "the release must state that real-time positions are not covered: " +
                coverage.notCovered.map { it.resolve("en") },
        )
        assertNotNull(coverage.note)
        listOf("el", "en", "sq").forEach {
            assertTrue(coverage.note.resolve(it).isNotBlank(), "the note has no " + it + " text")
        }
        assertTrue(
            coverage.absenceSemantics.isNotEmpty(),
            "the release must say what the absence of a journey means",
        )
        assertNotNull(coverage.freshness, "coverage must carry its own freshness")
        assertTrue(coverage.operatorCount > 0)

        // The coverage range is a span, not an inventory. The release describes a
        // period, but publishes a timetable only for the dates its data names, so
        // there are dates inside the range with no pack. That gap is precisely why
        // the core must answer "no offline data for this date" rather than "no
        // service on this date", and this asserts the gap is real in shipped data
        // rather than only in a unit test's imagination.
        val range = assertNotNull(coverage.serviceDates)
        assertNotNull(range.from)
        assertNotNull(range.to)
        Release.serviceDates.forEach {
            assertTrue(range.contains(it), "published date " + it + " is outside the stated range")
        }
        val insideButUnpublished = generateSequence(range.from) {
            dev.peterdsp.odivrelo.core.time.ServiceTime.shiftServiceDate(it, 1)
        }
            .takeWhile { it <= range.to!! }
            .firstOrNull { it !in Release.serviceDates }
        assertNotNull(
            insideButUnpublished,
            "the release should exercise a date inside its own coverage range that " +
                "still has no published timetable",
        )
    }

    @Test
    fun placesPackDistinguishesTerminalsFromBoardingPoints() {
        val pack = OdivreloJson.instance.decodeFromString(
            PlaceResults.serializer(),
            Release.packText(PackNames.PLACES),
        )
        assertEquals(Release.releaseId, pack.releaseId)
        assertEquals(pack.places.size, pack.total)

        val terminals = pack.places.filter { it.kind == PlaceKind.STOP_PLACE }
        val boardingPoints = pack.places.filter { it.kind == PlaceKind.STOP }
        assertTrue(terminals.isNotEmpty(), "a release must publish at least one terminal")
        assertTrue(boardingPoints.isNotEmpty())
        terminals.forEach { assertNull(it.parentId, "a terminal must not have a parent") }

        // At least one terminal has several boarding points, which is the case the
        // interface has to disambiguate before a traveller commits to a search.
        val disambiguating = terminals.first { it.boardingPointCount > 1 }
        assertEquals(
            disambiguating.boardingPointCount,
            pack.places.count { it.parentId == disambiguating.id },
            "a terminal's declared boarding-point count must match its children",
        )

        pack.places.forEach { place ->
            assertTrue(place.name.resolve("el").isNotBlank(), "${place.id} has no Greek name")
            assertTrue(place.name.resolve("en").isNotBlank(), "${place.id} has no English name")
            assertTrue(place.name.resolve("sq").isNotBlank(), "${place.id} has no Albanian name")
        }
    }

    @Test
    fun stopsPackIsKeyedByIdAndCarriesBoardingPointsAndProvenance() {
        val pack = OdivreloJson.instance.decodeFromString(
            StopsPack.serializer(),
            Release.packText(PackNames.STOPS),
        )
        assertEquals(Release.releaseId, pack.releaseId)
        assertTrue(pack.stops.isNotEmpty())
        pack.stops.forEach { (id, stop) ->
            assertEquals(id, stop.id, "the stops pack key must be the stop id")
            assertTrue(stop.provenance.isNotEmpty(), "$id has no provenance")
            assertNotNull(stop.correctionUrl, "$id offers no correction path")
        }

        // The pack's own departures are full journey results, the same shape a
        // search returns. The core recomputes them for the date being shown, but
        // they must still decode, because a shape change here is a contract change.
        val withDepartures = pack.stops.values.filter { it.departures.isNotEmpty() }
        assertTrue(withDepartures.isNotEmpty(), "the stops pack should carry some departures")
        withDepartures.forEach { stop ->
            stop.departures.forEach { journey ->
                assertTrue(journey.id.isNotBlank())
                assertTrue(journey.departure.at.isNotBlank())
                assertEquals(PositionQuality.SCHEDULED, journey.positionQuality)
            }
        }

        val withBays = pack.stops.values.first { it.boardingPoints.size > 1 }

        // Step free has three states and the third one is not "false". A boarding
        // point nobody has reviewed must stay null all the way to the screen.
        val stepFreeStates = withBays.boardingPoints.map { it.stepFree }.toSet()
        assertTrue(
            stepFreeStates.contains(true) && stepFreeStates.contains(null),
            "the release must exercise both reviewed step free and not reviewed: $stepFreeStates",
        )
    }

    @Test
    fun operatorsPackIsKeyedByIdAndCarriesRightsBearingSources() {
        val pack = OdivreloJson.instance.decodeFromString(
            OperatorsPack.serializer(),
            Release.packText(PackNames.OPERATORS),
        )
        assertEquals(Release.releaseId, pack.releaseId)
        assertTrue(pack.operators.isNotEmpty())
        pack.operators.forEach { (id, operator) ->
            assertEquals(id, operator.id, "the operators pack key must be the operator id")
            assertTrue(operator.name.resolve("el").isNotBlank())
            assertTrue(operator.sources.isNotEmpty(), "$id has no sources")
            assertNotNull(operator.correctionUrl, "$id offers no correction path")
            assertEquals(CoverageState.DEMO, operator.coverage.state)
        }
    }

    @Test
    fun sourcesPackCarriesRightsStatusAndLicencePerSource() {
        val pack = OdivreloJson.instance.decodeFromString(
            SourceList.serializer(),
            Release.packText(PackNames.SOURCES),
        )
        assertEquals(Release.releaseId, pack.releaseId)
        assertTrue(pack.sources.isNotEmpty())
        pack.sources.forEach { source ->
            assertTrue(source.name.isNotBlank(), source.id + " has no name")
        }
        // The registry records sources the product is not allowed to publish from.
        // A recorded licence does not by itself grant permission, so a source can
        // carry one while its rights are still unreviewed. What must never happen is
        // such a source reading as publishable.
        val restricted = pack.sources.filterNot { it.rightsStatus == RightsStatus.ALLOWED }
        assertTrue(
            restricted.isNotEmpty(),
            "the registry should record at least one source the product may not use",
        )
        restricted.forEach {
            assertFalse(
                it.rightsStatus.isPublishable,
                it.id + " has rights status " + it.rightsStatus + " but reads as publishable",
            )
        }
    }

    @Test
    fun aJourneysPackCarriesTheDayListAndADetailForEveryResult() {
        Release.serviceDates.forEach { date ->
            val day = Release.dayPack(date)
            assertEquals(Release.releaseId, day.releaseId)
            assertEquals(date, day.serviceDate, "pack for $date declares a different date")
            assertTrue(day.results.isNotEmpty(), "the pack for $date lists no journeys")

            day.results.forEach { summary ->
                val detail = day.journeys[summary.id]
                assertNotNull(detail, "$date has no detail for ${summary.id}")
                assertEquals(summary.id, detail.id)
                assertEquals(date, detail.serviceDate)
                assertTrue(detail.stops.size >= 2, "${summary.id} has fewer than two stops")
                assertEquals(
                    detail.stops.map { it.sequence }.sorted(),
                    detail.stops.map { it.sequence },
                    "${summary.id} stop sequence is out of order",
                )
                assertNotNull(detail.boardingPoint, "${summary.id} has no reviewed boarding point")
                assertTrue(detail.provenance.isNotEmpty(), "${summary.id} has no provenance")
                assertNotNull(detail.correctionUrl, "${summary.id} offers no correction path")

                // This release publishes schedules only. Nothing in it is observed.
                assertEquals(PositionQuality.SCHEDULED, summary.positionQuality)
                assertEquals(PositionQuality.SCHEDULED, detail.positionQuality)
                assertFalse(summary.positionQuality.isRealTime)

                summary.fare?.let {
                    assertTrue(it.isIndicative, "a published fare must be indicative, never a quote")
                }
            }
        }
    }

    @Test
    fun aJourneysPackExercisesBoardingRulesAndTimeQuality() {
        val day = Release.dayPack(Release.busiestDate)
        val allStops = day.journeys.values.flatMap { it.stops }

        // A first stop cannot be got off at and a last stop cannot be boarded at.
        day.journeys.values.forEach { detail ->
            val ordered = detail.stops.sortedBy { it.sequence }
            assertEquals(BoardingRule.NOT_ALLOWED, ordered.first().dropoff)
            assertEquals(BoardingRule.NOT_ALLOWED, ordered.last().pickup)
            assertNull(ordered.first().arrivalAt)
            assertNull(ordered.last().departureAt)
        }

        assertTrue(
            allStops.any { it.timeQuality == TimeQuality.APPROXIMATE },
            "the release must exercise an approximate time so the interface has to label it",
        )
        assertTrue(allStops.any { it.timeQuality == TimeQuality.SCHEDULED })
    }

    @Test
    fun theReleaseExercisesAnOvernightJourneyThatKeepsTheEarlierServiceDate() {
        val overnight = Release.serviceDates
            .flatMap { date -> Release.dayPack(date).results.map { date to it } }
            .firstOrNull { it.second.crossesMidnight }
        assertNotNull(overnight, "the release must exercise a journey that crosses midnight")

        val (date, journey) = overnight
        assertEquals(date, journey.serviceDate, "an overnight journey keeps the earlier date")
        val departure = ServiceDateProbe.instant(journey.departure.at)
        val arrival = ServiceDateProbe.instant(journey.arrival.at)
        assertNotNull(departure)
        assertNotNull(arrival)
        assertTrue(arrival > departure)
        assertTrue(
            dev.peterdsp.odivrelo.core.time.ServiceTime.crossesMidnight(departure, arrival),
            "the core must agree that this journey crosses midnight",
        )
        assertEquals(
            journey.durationMinutes,
            dev.peterdsp.odivrelo.core.time.ServiceTime.durationMinutes(departure, arrival),
            "the published duration must match the published instants",
        )
    }

    @Test
    fun everyPublishedTimestampCarriesAnExplicitOffset() {
        Release.serviceDates.forEach { date ->
            Release.dayPack(date).journeys.values.forEach { detail ->
                (listOf(detail.departure.at, detail.arrival.at) +
                    detail.stops.mapNotNull { it.arrivalAt } +
                    detail.stops.mapNotNull { it.departureAt })
                    .forEach { stamp ->
                        assertTrue(
                            stamp.endsWith("Z") ||
                                stamp.substring(stamp.length - 6).let {
                                    it.startsWith("+") || it.startsWith("-")
                                },
                            "timestamp '$stamp' has no explicit offset",
                        )
                        assertNotNull(
                            ServiceDateProbe.instant(stamp),
                            "timestamp '$stamp' does not parse",
                        )
                    }
            }
        }
    }

    @Test
    fun aGeometryThatWasRejectedIsNeverDrawn() {
        assertFalse(GeometryConfidence.REJECTED.isDrawable)
        assertTrue(GeometryConfidence.UNVERIFIED.isDrawable)
        assertTrue(GeometryConfidence.ORDERED_STOPS_ONLY.isDrawable)
    }

    @Test
    fun unknownEnumerationMembersAreNotSilentlyPermissive() {
        // The difference between showing a timetable and not being allowed to.
        assertFalse(RightsStatus.UNKNOWN.isPublishable)
        assertFalse(RightsStatus.PERMISSION_PENDING.isPublishable)
        assertFalse(RightsStatus.PROHIBITED.isPublishable)
        assertTrue(RightsStatus.ALLOWED.isPublishable)

        assertTrue(DataMode.DEMO.requiresDemoNotice)
        assertFalse(DataMode.REAL.requiresDemoNotice)
    }

    @Test
    fun additiveUnknownFieldsDoNotBreakAnInstalledClient() {
        val json = """
            {
              "contractVersion": "1.0.0",
              "releaseId": "abc",
              "publishedAt": "2026-09-30T10:00:00Z",
              "dataMode": "demo",
              "somethingAddedLater": {"nested": [1, 2, 3]},
              "places": [],
              "total": 0
            }
        """.trimIndent()
        val pack = OdivreloJson.instance.decodeFromString(PlaceResults.serializer(), json)
        assertEquals("abc", pack.releaseId)
        assertTrue(pack.places.isEmpty())
    }

    @Test
    fun localisedTextFallsBackToGreekThenEnglish() {
        assertEquals("Ελληνικά", LocalizedText(el = "Ελληνικά").resolve("sq"))
        assertEquals("English", LocalizedText(en = "English").resolve("el"))
        assertEquals("Shqip", LocalizedText(sq = "Shqip").resolve("de"))
        assertEquals("", LocalizedText().resolve("el"))
        assertEquals("Ελληνικά", LocalizedText(el = "Ελληνικά", en = "English").resolve("el-GR"))
    }

    @Test
    fun everyFileTheManifestNamesIsPresentAsAFixture() {
        val names = fixtureNames()
        assertTrue(Release.MANIFEST in names, "manifest fixture missing")
        assertTrue("corrupt/journeys-corrupt.json" in names)
        assertTrue("corrupt/journeys-other-release.json" in names)
        Release.manifest.files.values.forEach { file ->
            assertTrue(file.path in names, "missing ${file.path}")
        }
    }
}

/** A tiny indirection so the decoding tests do not import the time module wholesale. */
private object ServiceDateProbe {
    fun parse(value: String) = dev.peterdsp.odivrelo.core.time.ServiceTime.parseServiceDateOrNull(value)
    fun instant(value: String) = dev.peterdsp.odivrelo.core.time.ServiceTime.parseInstantOrNull(value)
}
