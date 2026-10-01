import Foundation
import Testing
@testable import Odivrelo

/// The fixture must never be reachable in a Release build.
@Suite("Release guard")
struct ReleaseGuardTests {
    @Test("Fixtures are reachable only in Debug")
    func fixtureReachabilityMatchesConfiguration() {
        #if DEBUG
        #expect(CoreClientFactory.fixturesAreReachable)
        #else
        #expect(!CoreClientFactory.fixturesAreReachable)
        #endif
    }

    @Test("A Release build never selects the fixture")
    func releaseNeverSelectsFixture() {
        let config = CoreConfig(
            apiBaseUrl: nil,
            staticPacksBaseUrl: nil,
            packsDirectory: NSTemporaryDirectory(),
            databasePath: NSTemporaryDirectory() + "guard.sqlite",
            languageTag: "el"
        )
        let (_, selection) = CoreClientFactory.make(config: config)
        #if DEBUG
        #expect(selection == .sharedCore || selection == .fixture)
        #else
        #expect(selection != .fixture)
        #endif
    }

    /// Asked for explicitly, a Release build still refuses. The compile-time
    /// guard is that `FixtureCoreClient` cannot be named outside Debug at all;
    /// this asserts the runtime consequence.
    @Test("Asking for a fixture in Release yields something else")
    func releaseRefusesAnExplicitFixtureRequest() {
        let config = CoreConfig(
            apiBaseUrl: nil,
            staticPacksBaseUrl: nil,
            packsDirectory: NSTemporaryDirectory(),
            databasePath: NSTemporaryDirectory() + "guard.sqlite",
            languageTag: "el"
        )
        let (_, selection) = CoreClientFactory.make(config: config, preferFixture: true)
        #if DEBUG
        #expect(selection == .fixture)
        #else
        #expect(selection != .fixture)
        #endif
    }

    @Test("A build without the core reports it rather than inventing data")
    func unavailableClientAlwaysFails() async {
        let client = UnavailableCoreClient(reason: "test")
        await #expect(throws: CoreError.self) { try await client.meta() }
        await #expect(throws: CoreError.self) { try await client.coverage() }
        await #expect(throws: CoreError.self) { try await client.searchPlaces(query: "a", limit: 5) }
        await #expect(throws: CoreError.self) { try await client.offlineCatalog() }

        // Freshness has no rule without a core, so it reports stale with no
        // check time rather than a comforting default.
        let freshness = client.freshnessOf(checkedAt: Date(), now: Date())
        #expect(freshness.state == .stale)
        #expect(freshness.checkedAt == nil)
    }
}

/// The service date is a value, not a calculator.
@Suite("Service date")
struct ServiceDateTests {
    @Test("Well-formed dates parse and round-trip")
    func parsesValidDates() throws {
        let date = try #require(ServiceDate(iso: "2026-10-02"))
        #expect(date.iso == "2026-10-02")
        #expect(date.year == 2026)
        #expect(date.month == 10)
        #expect(date.day == 2)
        #expect(date.description == "2026-10-02")
    }

    @Test("Malformed dates are refused", arguments: [
        "2026-10-2", "26-10-02", "2026/10/02", "", "2026-13-01", "2026-10-32", "not a date",
    ])
    func refusesMalformedDates(_ raw: String) {
        #expect(ServiceDate(iso: raw) == nil)
    }

    @Test("Dates sort chronologically")
    func sortsChronologically() throws {
        let earlier = try #require(ServiceDate(iso: "2026-09-30"))
        let later = try #require(ServiceDate(iso: "2026-10-01"))
        #expect(earlier < later)
        #expect(!(later < earlier))
    }

    @Test("Codable uses the contract's string form")
    func encodesAsAString() throws {
        let date = try #require(ServiceDate(iso: "2026-10-02"))
        let data = try JSONEncoder().encode(date)
        #expect(String(data: data, encoding: .utf8) == "\"2026-10-02\"")
        #expect(try JSONDecoder().decode(ServiceDate.self, from: data) == date)
    }

    @Test("The picker round-trips through Europe/Athens")
    func pickerRoundTrips() throws {
        let date = try #require(ServiceDate(iso: "2026-10-02"))
        #expect(ServiceDate.fromPicker(date.pickerInstant) == date)
    }

    /// The last Sunday in March is the daylight-saving gap in Europe/Athens.
    /// Noon is used precisely so the picker cannot land on an absent instant.
    @Test("The picker survives the daylight-saving transitions")
    func pickerSurvivesDaylightSaving() throws {
        for iso in ["2026-03-29", "2026-10-25"] {
            let date = try #require(ServiceDate(iso: iso))
            #expect(ServiceDate.fromPicker(date.pickerInstant) == date, "failed on \(iso)")
        }
    }

    @Test("Europe/Athens is the contract's zone")
    func usesTheContractZone() {
        #expect(ServiceDate.zone.identifier == "Europe/Athens")
        #expect(ServiceDate.calendar.timeZone.identifier == "Europe/Athens")
    }
}

/// The instants the core exchanges carry an explicit offset.
@Suite("Contract instants")
struct ContractInstantTests {
    @Test("Instants with an offset parse")
    func parsesOffsetInstants() throws {
        let value = try #require(ContractInstant.parse("2026-10-02T09:00:00+03:00"))
        // 09:00 at +03:00 is 06:00 UTC. Built from components rather than a
        // hand-computed constant, so the test states the fact it is checking.
        var components = DateComponents()
        components.year = 2026
        components.month = 10
        components.day = 2
        components.hour = 6
        components.timeZone = TimeZone(identifier: "UTC")
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let expected = try #require(utc.date(from: components))
        #expect(abs(value.timeIntervalSince(expected)) < 1)
    }

    @Test("The same instant in two offsets is the same moment")
    func equivalentOffsetsAgree() throws {
        let athens = try #require(ContractInstant.parse("2026-10-02T09:00:00+03:00"))
        let utc = try #require(ContractInstant.parse("2026-10-02T06:00:00Z"))
        #expect(abs(athens.timeIntervalSince(utc)) < 1)
    }

    @Test("Fractional seconds parse too")
    func parsesFractionalSeconds() {
        #expect(ContractInstant.parse("2026-10-02T09:00:00.250+03:00") != nil)
    }

    @Test("Empty and malformed values yield nil", arguments: ["", "not a time", "2026-10-02"])
    func refusesMalformedInstants(_ raw: String) {
        #expect(ContractInstant.parse(raw) == nil)
    }

    @Test("Formatting round-trips to the same instant")
    func roundTrips() throws {
        let original = Date(timeIntervalSince1970: 1_791_615_600)
        let text = ContractInstant.format(original)
        let parsed = try #require(ContractInstant.parse(text))
        #expect(abs(parsed.timeIntervalSince(original)) < 1)
    }
}

/// Unknown enumeration values must be preserved as a stated unknown, never
/// silently mapped onto a reassuring case.
@Suite("Contract enumerations")
struct ContractEnumTests {
    @Test("An unknown rights status is not reported as allowed")
    func unknownRightsIsNotAllowed() {
        #expect(RightsStatus.fromKotlinName("somethingNew") == .unknown)
        #expect(RightsStatus.fromKotlinName("somethingNew") != .allowed)
    }

    @Test("An unknown coverage state is not reported as covered")
    func unknownCoverageIsNotCovered() {
        #expect(CoverageState.fromKotlinName("brandNew") == .notCovered)
    }

    @Test("An unknown boarding rule is not reported as allowed")
    func unknownBoardingRuleIsNotAllowed() {
        #expect(BoardingRule.fromKotlinName("mystery") == .notAllowed)
    }

    @Test("An unknown position quality falls back to scheduled, never live")
    func unknownPositionQualityIsNotLive() {
        #expect(PositionQuality.fromKotlinName("teleport") == .scheduled)
        #expect(!PositionQuality.fromKotlinName("teleport").isRealTime)
    }

    @Test("An unknown confidence is not reported as reviewed")
    func unknownConfidenceIsNotReviewed() {
        #expect(Confidence.fromKotlinName("audited") == .candidate)
    }

    /// The two answers are different facts. Conflating them would tell a
    /// traveller that nothing runs that day when all that is known is that this
    /// device has no timetable for it.
    @Test("An unknown unavailable reason is never reported as 'no service'")
    func unknownReasonIsNotNoService() {
        #expect(UnavailableReason.fromKotlinName("somethingNew") == .unstated)
        #expect(UnavailableReason.fromKotlinName("somethingNew") != .noServiceOnDate)
        #expect(UnavailableReason.fromKotlinName("somethingNew") != .noOfflineDataForDate)
    }

    @Test("Missing offline data and missing service are distinct cases")
    func offlineGapIsDistinctFromNoService() {
        #expect(UnavailableReason.noOfflineDataForDate != .noServiceOnDate)
        #expect(UnavailableReason.fromKotlinName("noOfflineDataForDate") == .noOfflineDataForDate)
        #expect(UnavailableReason.fromKotlinName("noServiceOnDate") == .noServiceOnDate)
    }

    @Test("An unknown data mode keeps the demonstration notice on")
    func unknownDataModeStillWarns() {
        #expect(DataMode.fromKotlinName("staging") == .demo)
        #expect(DataMode.fromKotlinName("staging").requiresDemoNotice)
    }

    @Test("Known Kotlin names map exactly")
    func knownNamesMap() {
        #expect(RightsStatus.fromKotlinName("permissionPending") == .permissionPending)
        #expect(CoverageState.fromKotlinName("notCovered") == .notCovered)
        #expect(GeometryConfidence.fromKotlinName("orderedStopsOnly") == .orderedStopsOnly)
        #expect(PurchaseKind.fromKotlinName("ticketOffice") == .ticketOffice)
        #expect(PackPhase.fromKotlinName("verifying") == .verifying)
        #expect(PackFailure.fromKotlinName("digestMismatch") == .digestMismatch)
    }

    @Test("A rejected route shape is never drawable")
    func rejectedGeometryIsNotDrawable() {
        #expect(!GeometryConfidence.rejected.isDrawable)
        #expect(GeometryConfidence.orderedStopsOnly.isDrawable)
    }

    @Test("Only verified and published records are showable")
    func showableReviewStates() {
        #expect(ReviewState.published.isShowable)
        #expect(ReviewState.verified.isShowable)
        #expect(!ReviewState.candidate.isShowable)
        #expect(!ReviewState.withdrawn.isShowable)
        #expect(!ReviewState.quarantined.isShowable)
    }
}

/// Localised text falls back rather than showing nothing.
@Suite("Localised text")
struct LocalisedTextTests {
    @Test("The requested language wins")
    func prefersRequestedLanguage() {
        let text = LocalisedText(el: "Αθήνα", en: "Athens", sq: "Athina")
        #expect(text.resolved(for: "el") == "Αθήνα")
        #expect(text.resolved(for: "en") == "Athens")
        #expect(text.resolved(for: "sq") == "Athina")
    }

    @Test("A regional tag resolves to its base language")
    func handlesRegionalTags() {
        let text = LocalisedText(el: "Αθήνα", en: "Athens", sq: "Athina")
        #expect(text.resolved(for: "en-GB") == "Athens")
        #expect(text.resolved(for: "el-GR") == "Αθήνα")
    }

    @Test("A missing language falls back rather than showing nothing")
    func fallsBackWhenMissing() {
        let text = LocalisedText(el: "Αθήνα", en: nil, sq: nil)
        #expect(text.resolved(for: "en") == "Αθήνα")
        #expect(text.resolved(for: "sq") == "Αθήνα")
    }

    @Test("An empty string counts as missing")
    func treatsEmptyAsMissing() {
        let text = LocalisedText(el: "", en: "Athens", sq: "")
        #expect(text.resolved(for: "el") == "Athens")
    }

    @Test("Nothing at all resolves to an empty string, not a crash")
    func handlesNothing() {
        let text = LocalisedText(el: nil, en: nil, sq: nil)
        #expect(text.resolved(for: "el").isEmpty)
        #expect(text.isEmpty)
    }
}

/// Every contract error maps to its own presentation and its own next step.
@Suite("Error presentation")
@MainActor
struct ErrorPresentationTests {
    @Test("Each contract code gets a distinct title")
    func contractCodesAreDistinct() {
        let errors: [CoreError] = [
            .notFound, .invalidRequest(field: "date"), .unavailable(kind: .noDataInstalled),
            .releaseMismatch, .unauthorized, .offline,
            .coreUnavailable(reason: "missing"),
        ]
        let titles = Set(errors.map { ErrorPresentation.of($0).title })
        #expect(titles.count == errors.count, "two errors share a title: \(titles)")
    }

    @Test("A missing core is never presented as a network problem")
    func missingCoreIsNotANetworkProblem() {
        let presentation = ErrorPresentation.of(.coreUnavailable(reason: "not linked"))
        #expect(presentation.title != ErrorPresentation.of(.offline).title)
        #expect(!presentation.isRetryable)
    }

    @Test("A full disk is not offered as retryable")
    func storageFullIsNotRetryable() {
        let presentation = ErrorPresentation.of(
            .pack(.storageFull, packName: "places", message: nil)
        )
        #expect(!presentation.isRetryable)
    }

    @Test("A corrupt download is offered as retryable")
    func corruptDownloadIsRetryable() {
        let presentation = ErrorPresentation.of(
            .pack(.digestMismatch, packName: "places", message: "expected abc")
        )
        #expect(presentation.isRetryable)
    }

    @Test("Each pack failure gets a distinct title")
    func packFailuresAreDistinct() {
        let titles = Set(
            PackFailure.allCases.map {
                ErrorPresentation.of(.pack($0, packName: "p", message: nil)).title
            }
        )
        // network and timeout deliberately share the offline wording.
        #expect(titles.count >= PackFailure.allCases.count - 1)
    }

    @Test("A cancelled pack download is reported as cancelled, not failed")
    func cancellationIsNotAFailure() {
        let result = PackResult(
            packName: "places", succeeded: false, installed: nil,
            failure: .cancelled, message: nil, attempts: 1
        )
        #expect(CoreErrorTranslation.fromPack(result) == .cancelled)
    }

    @Test("A network pack failure becomes the offline state")
    func networkFailureBecomesOffline() {
        let result = PackResult(
            packName: "places", succeeded: false, installed: nil,
            failure: .network, message: nil, attempts: 2
        )
        #expect(CoreErrorTranslation.fromPack(result) == .offline)
    }

    @Test("Contract codes translate to their own error")
    func contractCodeTranslation() {
        #expect(CoreErrorTranslation.fromContract(code: .notFound, field: nil) == .notFound)
        #expect(
            CoreErrorTranslation.fromContract(code: .invalidRequest, field: "date")
                == .invalidRequest(field: "date")
        )
        #expect(CoreErrorTranslation.fromContract(code: .releaseMismatch, field: nil) == .releaseMismatch)
    }

    @Test("A lost connection is reported as offline, not as a server error")
    func urlErrorsBecomeOffline() {
        let error = NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)
        #expect(CoreErrorTranslation.translate(error) == .offline)
    }

    @Test("A cancelled request is reported as cancelled")
    func cancellationTranslates() {
        let error = NSError(domain: NSURLErrorDomain, code: NSURLErrorCancelled)
        #expect(CoreErrorTranslation.translate(error) == .cancelled)
    }
}

/// The demonstration notice must be a pure function of the release's data mode,
/// so flipping a release from `demo` to `real` removes it and changes nothing
/// else.
@Suite("Demonstration notice")
struct DemonstrationNoticeTests {
    @Test("Only the demo data mode requires the notice")
    func onlyDemoRequiresTheNotice() {
        #expect(DataMode.demo.requiresDemoNotice)
        #expect(!DataMode.real.requiresDemoNotice)
    }

    @Test("An unrecognised data mode still requires the notice")
    func unknownModeStillWarns() {
        // Failing towards showing the notice is the safe direction: a release
        // this build does not understand must not silently look like real data.
        #expect(DataMode.fromKotlinName("staging").requiresDemoNotice)
    }

    @Test("The notice text names the region and denies that it is real",
          arguments: ["el", "en", "sq"])
    func noticeTextIsHonest(_ language: String) {
        L10n.use(languageTag: language)
        defer { L10n.use(languageTag: nil) }

        let body = L10n.demoBody
        #expect(body.contains("Aloria") || body.contains("Αλορία") || body.contains("Alorin"),
                "\(language): the notice must name the invented region: \(body)")
        #expect(!body.isEmpty)
        #expect(!L10n.demoTitle.isEmpty)
    }
}

/// The enum boundary: a Kotlin constant name maps onto the matching Swift case.
///
/// The core hands the app each enum as its Kotlin `name`, which is
/// SCREAMING_SNAKE_CASE. These pin the mapping so the real core does not quietly
/// decode every enum to its unrecognised fallback, which once hid the boarding
/// rules, the time quality and the segment role behind uniform defaults.
@Suite("Kotlin enum names")
struct KotlinEnumNameTests {
    @Test("Boarding rules map from their Kotlin names")
    func boardingRules() {
        #expect(BoardingRule.fromKotlinName("ALLOWED") == .allowed)
        #expect(BoardingRule.fromKotlinName("NOT_ALLOWED") == .notAllowed)
        #expect(BoardingRule.fromKotlinName("ON_REQUEST") == .onRequest)
        #expect(BoardingRule.fromKotlinName("COORDINATE_WITH_OPERATOR") == .coordinateWithOperator)
    }

    @Test("Segment roles map from their Kotlin names")
    func segmentRoles() {
        #expect(SegmentRole.fromKotlinName("BOARD") == .board)
        #expect(SegmentRole.fromKotlinName("ON_SEGMENT") == .onSegment)
        #expect(SegmentRole.fromKotlinName("ALIGHT") == .alight)
        #expect(SegmentRole.fromKotlinName("BEFORE_BOARD") == .beforeBoard)
        #expect(SegmentRole.fromKotlinName("AFTER_ALIGHT") == .afterAlight)
    }

    @Test("Other contract enums map from their Kotlin names")
    func otherEnums() {
        #expect(TimeQuality.fromKotlinName("SCHEDULED") == .scheduled)
        #expect(Confidence.fromKotlinName("REVIEWED") == .reviewed)
        #expect(ReviewState.fromKotlinName("PUBLISHED") == .published)
    }

    @Test("An already lowerCamelCase name still maps, and an unknown falls back")
    func directAndUnknown() {
        #expect(BoardingRule.fromKotlinName("notAllowed") == .notAllowed)
        #expect(SegmentRole.fromKotlinName("SOMETHING_NEW") == .onSegment)
    }
}
