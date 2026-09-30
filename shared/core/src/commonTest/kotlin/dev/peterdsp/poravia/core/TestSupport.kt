package dev.peterdsp.poravia.core

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import dev.peterdsp.poravia.core.db.PoraviaDatabase
import dev.peterdsp.poravia.core.io.Paths
import dev.peterdsp.poravia.core.io.PlatformFiles
import dev.peterdsp.poravia.core.model.OfflineManifest
import dev.peterdsp.poravia.core.model.Place
import dev.peterdsp.poravia.core.model.PlaceKind
import dev.peterdsp.poravia.core.model.PlaceResults
import dev.peterdsp.poravia.core.packs.JourneysPack
import dev.peterdsp.poravia.core.packs.PackNames
import dev.peterdsp.poravia.core.serialization.PoraviaJson
import dev.peterdsp.poravia.core.time.ServiceTime
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** A writable directory unique to one test, so tests never see each other's packs. */
internal expect fun createTestDirectory(name: String): String

/** An in-memory database created from the given schema. */
internal expect fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver

/** An in-memory database at the current schema version. */
internal fun createTestDriver(): SqlDriver = createDriver(PoraviaDatabase.Schema)

/**
 * The shipped version 1 schema, kept here verbatim so the migration test starts
 * from what a beta tester's phone actually holds rather than from a guess.
 *
 * Version 1 had no `boarding_step_free` column on `saved_trip`.
 */
internal object SchemaVersion1 : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        STATEMENTS.forEach { driver.execute(null, it, 0) }
        return QueryResult.Unit
    }

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: app.cash.sqldelight.db.AfterVersion,
    ): QueryResult.Value<Unit> = PoraviaDatabase.Schema.migrate(
        driver,
        oldVersion,
        newVersion,
        *callbacks,
    )

    private val STATEMENTS = listOf(
        """
        CREATE TABLE favorite_place (
            place_id     TEXT NOT NULL PRIMARY KEY,
            name_json    TEXT NOT NULL,
            kind         TEXT NOT NULL,
            municipality TEXT,
            added_at     TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE recent_search (
            origin_id             TEXT NOT NULL,
            origin_name_json      TEXT NOT NULL,
            destination_id        TEXT NOT NULL,
            destination_name_json TEXT NOT NULL,
            service_date          TEXT NOT NULL,
            searched_at           TEXT NOT NULL,
            PRIMARY KEY (origin_id, destination_id, service_date)
        )
        """.trimIndent(),
        """
        CREATE TABLE saved_trip (
            id                     TEXT NOT NULL PRIMARY KEY,
            journey_id             TEXT NOT NULL,
            service_date           TEXT NOT NULL,
            saved_at               TEXT NOT NULL,
            origin_name_json       TEXT NOT NULL,
            destination_name_json  TEXT NOT NULL,
            operator_name_json     TEXT NOT NULL,
            departure_at           TEXT NOT NULL,
            arrival_at             TEXT NOT NULL,
            crosses_midnight       INTEGER NOT NULL DEFAULT 0,
            boarding_bay           TEXT,
            release_id             TEXT NOT NULL,
            cached_at              TEXT NOT NULL,
            detail_json            TEXT NOT NULL
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX saved_trip_journey_date ON saved_trip (journey_id, service_date)",
        """
        CREATE TABLE installed_pack (
            name                 TEXT NOT NULL PRIMARY KEY,
            release_id           TEXT NOT NULL,
            sha256               TEXT NOT NULL,
            bytes                INTEGER NOT NULL,
            installed_at         TEXT NOT NULL,
            published_at         TEXT NOT NULL,
            file_name            TEXT NOT NULL,
            previous_release_id  TEXT,
            previous_file_name   TEXT,
            previous_sha256      TEXT
        )
        """.trimIndent(),
        """
        CREATE TABLE release_state (
            id                    INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
            release_id            TEXT NOT NULL,
            published_at          TEXT NOT NULL,
            data_mode             TEXT NOT NULL,
            manifest_json         TEXT NOT NULL,
            updated_at            TEXT NOT NULL,
            previous_release_id   TEXT,
            previous_published_at TEXT,
            previous_manifest_json TEXT
        )
        """.trimIndent(),
    )
}

/** A clock frozen at a chosen instant, so freshness assertions are exact. */
internal class FixedClock(private var instant: Instant) : Clock {
    override fun now(): Instant = instant

    fun advanceTo(next: Instant) {
        instant = next
    }
}

/**
 * The one canonical published release, produced by `scripts/api-seed-demo.sh` and
 * compiled into the test binary by the `generateTestFixtures` Gradle task.
 *
 * Nothing is copied or hand-maintained: identifiers, service dates and digests
 * are read out of the release itself. Identifiers are opaque by contract, so the
 * tests discover them rather than spell them out, and a change to the publisher's
 * shape breaks these tests loudly instead of passing against a stale copy.
 *
 * The dataset is the invented region of Aloria. No operator, terminal, boarding
 * point or departure in it exists.
 */
internal object Release {

    const val MANIFEST: String = "manifest.json"

    val manifest: OfflineManifest by lazy {
        PoraviaJson.instance.decodeFromString(
            OfflineManifest.serializer(),
            TestFixtures.read(MANIFEST),
        )
    }

    val releaseId: String get() = manifest.releaseId

    /** Every service date the release publishes a journeys pack for, in order. */
    val serviceDates: List<String> by lazy {
        manifest.files.keys.mapNotNull { PackNames.serviceDateOf(it) }.sorted()
    }

    /** The published date with the most journeys, which is the useful one to search. */
    val busiestDate: String by lazy {
        serviceDates.maxByOrNull { date -> dayPack(date).results.size }
            ?: error("the release publishes no service date")
    }

    /**
     * A date the release deliberately publishes no pack for.
     *
     * A release only materialises the dates its data names, so this is an
     * ordinary situation. The core must report it as "no offline data for this
     * date" and never as "no service on this date".
     */
    val unpublishedDate: String by lazy {
        var candidate = "2026-12-01"
        while (candidate in serviceDates) {
            candidate = ServiceTime.shiftServiceDate(candidate, 1)
        }
        candidate
    }

    val journeysPackNames: List<String> by lazy {
        manifest.files.keys.filter { PackNames.isJourneys(it) }.sorted()
    }

    /** The journeys pack the fixture generator derived its broken variants from. */
    val brokenVariantSource: String by lazy {
        journeysPackNames.maxOrNull() ?: error("the release publishes no journeys pack")
    }

    fun packText(packName: String): String =
        TestFixtures.read(manifest.files.getValue(packName).path)

    fun packBytes(packName: String): ByteArray =
        TestFixtures.readBinary(manifest.files.getValue(packName).path)

    fun dayPack(serviceDate: String): JourneysPack = PoraviaJson.instance.decodeFromString(
        JourneysPack.serializer(),
        packText(PackNames.journeys(serviceDate)),
    )

    val places: List<Place> by lazy {
        PoraviaJson.instance
            .decodeFromString(PlaceResults.serializer(), packText(PackNames.PLACES))
            .places
    }

    /** A terminal with more than one boarding point: the case to disambiguate. */
    val terminalWithBays: Place by lazy {
        places.first { it.kind == PlaceKind.STOP_PLACE && it.boardingPointCount > 1 }
    }

    fun boardingPointsOf(terminalId: String): List<Place> =
        places.filter { it.parentId == terminalId }

    fun corruptDayText(): String = TestFixtures.read("corrupt/journeys-corrupt.json")

    fun otherReleaseDayText(): String = TestFixtures.read("corrupt/journeys-other-release.json")

    /**
     * Writes the release into a packs directory exactly as an installed release
     * looks on disk, so tests exercise the same reading path the application uses
     * rather than a shortcut.
     *
     * The manifest is written last, mirroring the publisher, so a half-written
     * directory reads as "nothing installed" rather than as a broken install.
     */
    fun install(
        packsDirectory: String,
        overrides: Map<String, String> = emptyMap(),
        omit: Set<String> = emptySet(),
    ) {
        val current = Paths.join(packsDirectory, "current")
        PlatformFiles.mkdirs(current)
        manifest.files.forEach { (name, file) ->
            if (name in omit) return@forEach
            val bytes = overrides[name]?.encodeToByteArray()
                ?: TestFixtures.readBinary(file.path)
            PlatformFiles.writeBytes(Paths.join(current, file.path), bytes)
        }
        PlatformFiles.writeBytes(
            Paths.join(current, MANIFEST),
            TestFixtures.read(MANIFEST).encodeToByteArray(),
        )
    }
}

/** Every fixture the generator produced, used to assert nothing went missing. */
internal fun fixtureNames(): Set<String> = TestFixtures.names()
