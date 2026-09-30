import SwiftUI
import Testing
@testable import Poravia

/// Layout is decided from the real container, never from a device.
///
/// These are geometry fixtures. They prove the resolver behaves correctly for a
/// given window, including asymmetric windows split by a reserved region. They
/// are **supplementary evidence**: they say nothing about whether a real
/// foldable reports such a region, because the installed SDK provides no API to
/// ask. See `ReservedRegions.swift` for exactly what was checked.
@Suite("Layout resolver")
struct LayoutResolverTests {
    static func geometry(
        width: CGFloat,
        height: CGFloat,
        safeArea: EdgeInsets = EdgeInsets(top: 59, leading: 0, bottom: 34, trailing: 0),
        reserved: [ReservedRegion] = [],
        keyboard: CGFloat = 0,
        horizontal: UserInterfaceSizeClass? = nil,
        dynamicType: DynamicTypeSize = .large
    ) -> WindowGeometry {
        WindowGeometry(
            size: CGSize(width: width, height: height),
            safeArea: safeArea,
            reserved: reserved,
            keyboardInset: keyboard,
            horizontalSizeClass: horizontal,
            verticalSizeClass: .regular,
            dynamicTypeSize: dynamicType
        )
    }

    @Test("A phone-width window is one column")
    func phoneIsSingleColumn() {
        let value = Self.geometry(width: 393, height: 852, horizontal: .compact)
        #expect(LayoutResolver.mode(for: value) == .singleColumn)
    }

    @Test("A compact size class stays one column however wide the window")
    func compactStaysSingleColumn() {
        let value = Self.geometry(width: 1_024, height: 768, horizontal: .compact)
        #expect(LayoutResolver.mode(for: value) == .singleColumn)
    }

    @Test("A regular window wide enough for two readable panes splits")
    func regularSplitsIntoTwo() {
        let value = Self.geometry(width: 1_024, height: 768, horizontal: .regular)
        #expect(LayoutResolver.mode(for: value) == .twoColumn)
    }

    @Test("A very wide window earns a third pane")
    func veryWideGetsThree() {
        let value = Self.geometry(width: 1_366, height: 1_024, horizontal: .regular)
        #expect(LayoutResolver.mode(for: value) == .threeColumn)
    }

    /// A regular-width iPad in a narrow Slide Over column must get the compact
    /// layout, because the column is narrow. That is the whole point of reading
    /// the container rather than the device.
    @Test("A narrow Slide Over column on a regular device is one column")
    func slideOverIsSingleColumn() {
        let value = Self.geometry(width: 320, height: 1_024, horizontal: .regular)
        #expect(LayoutResolver.mode(for: value) == .singleColumn)
    }

    @Test("A half-width Split View pane is one column")
    func halfSplitViewIsSingleColumn() {
        let value = Self.geometry(width: 507, height: 1_024, horizontal: .regular)
        #expect(LayoutResolver.mode(for: value) == .singleColumn)
    }

    @Test("A two-thirds Split View pane splits")
    func twoThirdsSplitViewSplits() {
        let value = Self.geometry(width: 694, height: 1_024, horizontal: .regular)
        #expect(LayoutResolver.mode(for: value) == .twoColumn)
    }

    @Test("The largest accessibility sizes force one column")
    func accessibilitySizesForceSingleColumn() {
        for size in [DynamicTypeSize.accessibility3, .accessibility4, .accessibility5] {
            let value = Self.geometry(width: 1_366, height: 1_024, horizontal: .regular, dynamicType: size)
            #expect(LayoutResolver.mode(for: value) == .singleColumn, "failed at \(size)")
        }
    }

    @Test("Below the largest accessibility sizes a wide window still splits")
    func moderateAccessibilitySizesStillSplit() {
        let value = Self.geometry(
            width: 1_366, height: 1_024, horizontal: .regular, dynamicType: .accessibility1
        )
        #expect(LayoutResolver.mode(for: value) != .singleColumn)
    }

    // MARK: Reserved regions

    /// A window split down the middle by a full-height reserved region, with
    /// both sides wide enough to read.
    @Test("A split window with two usable sides becomes two columns")
    func splitWindowWithUsableSidesSplits() {
        let region = ReservedRegion(
            id: "fixture.split",
            rect: CGRect(x: 420, y: 0, width: 24, height: 1_000),
            kind: .inferred
        )
        let value = Self.geometry(width: 880, height: 1_000, reserved: [region], horizontal: .regular)
        #expect(LayoutResolver.mode(for: value) == .twoColumn)
        #expect(value.splittingRegion?.id == "fixture.split")
    }

    /// The same split in a narrow window must not produce two unusable columns.
    @Test("A split window with an unusable side stays one column")
    func splitWindowWithNarrowSideStaysSingle() {
        let region = ReservedRegion(
            id: "fixture.split",
            rect: CGRect(x: 300, y: 0, width: 24, height: 1_000),
            kind: .inferred
        )
        let value = Self.geometry(width: 640, height: 1_000, reserved: [region], horizontal: .regular)
        #expect(LayoutResolver.mode(for: value) == .singleColumn)
    }

    @Test("An asymmetric split is reported with the larger side named")
    func asymmetryIsDetected() {
        let region = ReservedRegion(
            id: "fixture.split",
            rect: CGRect(x: 300, y: 0, width: 20, height: 1_000),
            kind: .inferred
        )
        let value = Self.geometry(width: 900, height: 1_000, reserved: [region])
        guard case let .trailingLarger(leading, trailing) = value.asymmetry else {
            Issue.record("expected the trailing side to be larger, got \(value.asymmetry)")
            return
        }
        #expect(leading == CGFloat(300))
        #expect(trailing == CGFloat(580))
    }

    @Test("A balanced split is reported as balanced")
    func balancedSplitIsBalanced() {
        let region = ReservedRegion(
            id: "fixture.split",
            rect: CGRect(x: 440, y: 0, width: 20, height: 1_000),
            kind: .inferred
        )
        let value = Self.geometry(width: 900, height: 1_000, reserved: [region])
        #expect(value.asymmetry == .balanced)
    }

    @Test("A window with no reserved region reports no asymmetry")
    func noRegionMeansNoAsymmetry() {
        let value = Self.geometry(width: 900, height: 1_000)
        #expect(value.asymmetry == .none)
        #expect(value.splittingRegion == nil)
    }

    @Test("A full-height reserved region reduces the usable width")
    func spanningRegionReducesUsableWidth() {
        let region = ReservedRegion(
            id: "fixture.spanning",
            rect: CGRect(x: 440, y: 0, width: 40, height: 1_000),
            kind: .systemReported
        )
        let value = Self.geometry(width: 900, height: 1_000, reserved: [region])
        #expect(value.usableWidth == CGFloat(860))
    }

    /// A strip that covers only part of the height is avoided by the views it
    /// would sit under, not subtracted from the whole window's width.
    @Test("A partial-height reserved region does not reduce the usable width")
    func partialRegionDoesNotReduceWidth() {
        let region = ReservedRegion(
            id: "fixture.partial",
            rect: CGRect(x: 440, y: 0, width: 40, height: 200),
            kind: .systemReported
        )
        let value = Self.geometry(width: 900, height: 1_000, reserved: [region])
        #expect(value.usableWidth == CGFloat(900))
    }

    @Test("The system source reports nothing on this SDK")
    func systemSourceReportsNothing() {
        // Honest by construction: the installed iOS 27.0 SDK exposes no public
        // reserved-region API, so the source reports none rather than guessing
        // a fold from an aspect ratio.
        let regions = SystemReservedRegionSource().regions(
            inContainerOfSize: CGSize(width: 1_398, height: 2_034),
            safeArea: EdgeInsets(top: 59, leading: 0, bottom: 34, trailing: 0)
        )
        #expect(regions.isEmpty)
    }

    // MARK: Keyboard and safe areas

    @Test("The keyboard reduces the usable height, not the width")
    func keyboardReducesHeightOnly() {
        let value = Self.geometry(width: 393, height: 852, keyboard: 336)
        #expect(value.keyboardIsVisible)
        #expect(value.usableHeight == CGFloat(852 - 59 - 34 - 336))
        #expect(value.usableWidth == CGFloat(393))
    }

    @Test("Safe areas are excluded from the usable width")
    func safeAreasReduceWidth() {
        let value = Self.geometry(
            width: 852, height: 393,
            safeArea: EdgeInsets(top: 0, leading: 59, bottom: 21, trailing: 59)
        )
        #expect(value.usableWidth == CGFloat(852 - 118))
    }

    @Test("Content width never exceeds the readable maximum")
    func contentWidthIsCapped() {
        let value = Self.geometry(width: 2_000, height: 1_200)
        #expect(LayoutResolver.contentWidth(for: value) == Theme.Size.contentMaximum)
    }

    @Test("A narrow window gets the smaller side padding")
    func narrowWindowUsesSmallPadding() {
        #expect(LayoutResolver.sidePadding(for: Self.geometry(width: 320, height: 800)) == Theme.Space.small)
        #expect(
            LayoutResolver.sidePadding(for: Self.geometry(width: 393, height: 800))
                == Theme.Space.mediumPlus
        )
    }

    /// The layout must depend only on geometry, so that holding the dataset,
    /// clock, locale, theme and network constant and changing only the window
    /// gives a deterministic answer.
    @Test("The same geometry always resolves to the same layout")
    func resolutionIsDeterministic() {
        let value = Self.geometry(width: 1_024, height: 768, horizontal: .regular)
        let answers = (0..<20).map { _ in LayoutResolver.mode(for: value) }
        #expect(Set(answers).count == 1)
    }

    /// Rotation is a geometry change and nothing else.
    @Test("Rotating a phone keeps it in one column")
    func rotationKeepsPhoneSingleColumn() {
        let portrait = Self.geometry(width: 393, height: 852, horizontal: .compact)
        let landscape = Self.geometry(
            width: 852, height: 393,
            safeArea: EdgeInsets(top: 0, leading: 59, bottom: 21, trailing: 59),
            horizontal: .compact
        )
        #expect(LayoutResolver.mode(for: portrait) == .singleColumn)
        #expect(LayoutResolver.mode(for: landscape) == .singleColumn)
    }
}

/// Deep links are validated, never trusted.
@Suite("Deep links")
struct DeepLinkTests {
    static let today = ServiceDate(iso: "2026-09-30")!

    static func parse(_ string: String) -> Result<DeepLinkRouter.Link, UnresolvedLink.Reason> {
        guard let url = URL(string: string) else { return .failure(.unknown) }
        return DeepLinkRouter.parse(url, today: today)
    }

    @Test("A universal link on the controlled domain resolves")
    func universalLinkResolves() {
        let result = Self.parse("https://poravia.peterdsp.dev/journey/jny.acl-0700?date=2026-10-02")
        guard case let .success(.journey(id, date)) = result else {
            Issue.record("expected a journey link, got \(result)")
            return
        }
        #expect(id == "jny.acl-0700")
        #expect(date.iso == "2026-10-02")
    }

    @Test("The custom scheme resolves the same way")
    func customSchemeResolves() {
        let result = Self.parse("poravia://journey/jny.acl-0700?date=2026-10-02")
        guard case let .success(.journey(id, _)) = result else {
            Issue.record("expected a journey link, got \(result)")
            return
        }
        #expect(id == "jny.acl-0700")
    }

    @Test("A link on another host is refused", arguments: [
        "https://example.com/journey/x?date=2026-10-02",
        "https://poravia.peterdsp.dev.evil.test/journey/x?date=2026-10-02",
        "https://notporavia.peterdsp.dev/journey/x?date=2026-10-02",
    ])
    func foreignHostsAreRefused(_ link: String) {
        guard case .failure(.unknown) = Self.parse(link) else {
            Issue.record("accepted a foreign host: \(link)")
            return
        }
    }

    @Test("Another app's scheme is refused")
    func foreignSchemeIsRefused() {
        guard case .failure(.unknown) = Self.parse("poravia-evil://journey/x?date=2026-10-02") else {
            Issue.record("accepted a foreign scheme")
            return
        }
    }

    @Test("A past service date is reported as expired, not opened")
    func pastDatesExpire() {
        let result = Self.parse("https://poravia.peterdsp.dev/journey/jny.a?date=2026-09-29")
        guard case let .failure(.expired(date)) = result else {
            Issue.record("expected expiry, got \(result)")
            return
        }
        #expect(date.iso == "2026-09-29")
    }

    @Test("Today itself is not expired")
    func todayIsNotExpired() {
        let result = Self.parse("https://poravia.peterdsp.dev/journey/jny.a?date=2026-09-30")
        guard case .success = result else {
            Issue.record("today should resolve, got \(result)")
            return
        }
    }

    /// `URLComponents.path` percent-decodes, so an encoded separator arrives
    /// already decoded and a naive character check lets `..` through.
    @Test("An identifier that could smuggle a path is refused", arguments: [
        "https://poravia.peterdsp.dev/journey/..%2F..%2Fetc?date=2026-10-02",
        "https://poravia.peterdsp.dev/journey/..%2f..%2fetc?date=2026-10-02",
        "https://poravia.peterdsp.dev/journey/..?date=2026-10-02",
        "https://poravia.peterdsp.dev/journey/.?date=2026-10-02",
        "https://poravia.peterdsp.dev/journey/a..b?date=2026-10-02",
        "https://poravia.peterdsp.dev/journey/a b?date=2026-10-02",
        "https://poravia.peterdsp.dev/journey/a%00b?date=2026-10-02",
        "https://poravia.peterdsp.dev/operator/..%2F..%2Fsecret",
        "https://poravia.peterdsp.dev/stop/..%2F..%2Fsecret",
    ])
    func hostileIdentifiersAreRefused(_ link: String) {
        guard case .failure = Self.parse(link) else {
            Issue.record("accepted a hostile identifier: \(link)")
            return
        }
    }

    /// A dot is legitimate inside a contract identifier, so the traversal fix
    /// must not reject ordinary ones.
    @Test("An ordinary dotted identifier is still accepted")
    func dottedIdentifiersStillWork() {
        let result = Self.parse(
            "https://poravia.peterdsp.dev/journey/jny.acl-0700?date=2026-10-02"
        )
        guard case let .success(.journey(id, _)) = result else {
            Issue.record("a normal identifier should resolve, got \(result)")
            return
        }
        #expect(id == "jny.acl-0700")
    }

    @Test("A journey link with no date is refused rather than guessed")
    func journeyWithoutDateIsRefused() {
        guard case .failure(.unknown) = Self.parse("https://poravia.peterdsp.dev/journey/jny.a") else {
            Issue.record("a journey link without a date should not be guessed")
            return
        }
    }

    @Test("An unknown path is reported as unknown")
    func unknownPathsAreUnknown() {
        guard case .failure(.unknown) = Self.parse("https://poravia.peterdsp.dev/nowhere/at/all") else {
            Issue.record("expected unknown")
            return
        }
    }

    @Test("Section links resolve", arguments: [
        ("poravia://offline", DeepLinkRouter.Link.offline),
        ("poravia://trips", DeepLinkRouter.Link.trips),
        ("poravia://settings", DeepLinkRouter.Link.settings),
        ("poravia://coverage", DeepLinkRouter.Link.coverage),
        ("poravia://sources", DeepLinkRouter.Link.sources),
    ])
    func sectionLinksResolve(_ link: String, _ expected: DeepLinkRouter.Link) {
        guard case let .success(value) = Self.parse(link) else {
            Issue.record("expected \(expected) from \(link)")
            return
        }
        #expect(value == expected)
    }

    @Test("A search link carries its places and date")
    func searchLinkCarriesItsQuery() {
        let result = Self.parse(
            "https://poravia.peterdsp.dev/search?origin=place.a&destination=place.b&date=2026-10-02"
        )
        guard case let .success(.search(origin, destination, date)) = result else {
            Issue.record("expected a search link, got \(result)")
            return
        }
        #expect(origin == "place.a")
        #expect(destination == "place.b")
        #expect(date?.iso == "2026-10-02")
    }
}

/// The empty-result wording must keep the two "nothing came back" answers
/// apart, in every language.
@Suite("Empty result wording")
struct EmptyResultWordingTests {
    @Test("No service and no offline data read differently in every language",
          arguments: ["el", "en", "sq"])
    func wordingIsDistinct(_ language: String) {
        L10n.use(languageTag: language)
        defer { L10n.use(languageTag: nil) }

        let noService = L10n.resultsEmptyNoService
        let noData = L10n.resultsEmptyNoOfflineData
        let unstated = L10n.resultsEmptyReasonUnstated

        #expect(noService != noData, "\(language): the two answers must not share wording")
        #expect(noService != unstated)
        #expect(noData != unstated)
        #expect(!noService.isEmpty)
        #expect(!noData.isEmpty)
    }

    @Test("The offline-gap hint claims nothing about whether a service runs",
          arguments: ["el", "en", "sq"])
    func offlineHintClaimsNothing(_ language: String) {
        L10n.use(languageTag: language)
        defer { L10n.use(languageTag: nil) }
        let hint = L10n.resultsEmptyNoOfflineDataHint
        #expect(!hint.isEmpty)
        #expect(hint != L10n.resultsEmptyNoService)
    }
}

/// Filters are collected in the shape the core expects.
@Suite("Journey filters")
struct JourneyFilterTests {
    @Test("A fresh filter set is inactive")
    func defaultIsInactive() {
        #expect(!JourneyFilters.none.isActive)
        #expect(JourneyFilters.none.activeCount == 0)
        #expect(JourneyFilters.none.includeCrossesMidnight)
    }

    @Test("Each dimension counts once")
    func activeCountCountsEachDimension() {
        var filters = JourneyFilters.none
        filters.accessibleOnly = true
        #expect(filters.activeCount == 1)
        filters.departAfter = "07:00"
        #expect(filters.activeCount == 2)
        filters.departBefore = "19:00"
        #expect(filters.activeCount == 2, "a window counts once, not twice")
        filters.operatorIds = ["op.a"]
        #expect(filters.activeCount == 3)
        filters.includeCrossesMidnight = false
        #expect(filters.activeCount == 4)
        filters.maxDurationMinutes = 300
        #expect(filters.activeCount == 5)
        filters.sort = .duration
        #expect(filters.activeCount == 6)
    }

    @Test("The departure window is collected as a local HH:mm")
    func windowIsLocalClockText() {
        #expect(FiltersSheet.clock(from: 0) == "00:00")
        #expect(FiltersSheet.clock(from: 7 * 60 + 5) == "07:05")
        #expect(FiltersSheet.clock(from: 23 * 60 + 59) == "23:59")
    }

    @Test("Clock text round-trips back to minutes")
    func windowRoundTrips() {
        for minutes in [0, 65, 435, 1_439] {
            #expect(FiltersSheet.minutes(from: FiltersSheet.clock(from: minutes)) == minutes)
        }
    }

    @Test("Malformed clock text yields nil", arguments: ["", "7:00:00", "abc", "07-00"])
    func refusesMalformedClockText(_ raw: String) {
        #expect(FiltersSheet.minutes(from: raw) == nil)
    }
}

/// Durations must not read as "0h 45m" or "3h 0m".
@Suite("Duration formatting")
@MainActor
struct DurationFormattingTests {
    @Test("A sub-hour duration omits the hours", arguments: ["el", "en", "sq"])
    func subHourOmitsHours(_ language: String) {
        L10n.use(languageTag: language)
        defer { L10n.use(languageTag: nil) }
        let text = Formatters.duration(minutes: 45)
        #expect(text.contains("45"))
        #expect(!text.contains("0"), "\(language): '\(text)' should not carry a zero hour")
    }

    @Test("A whole number of hours omits the minutes", arguments: ["el", "en", "sq"])
    func wholeHoursOmitMinutes(_ language: String) {
        L10n.use(languageTag: language)
        defer { L10n.use(languageTag: nil) }
        let text = Formatters.duration(minutes: 180)
        #expect(text.contains("3"))
        #expect(!text.contains("0"), "\(language): '\(text)' should not carry a zero minute")
    }

    @Test("A mixed duration carries both parts")
    func mixedCarriesBoth() {
        L10n.use(languageTag: "en")
        defer { L10n.use(languageTag: nil) }
        let text = Formatters.duration(minutes: 160)
        #expect(text.contains("2"))
        #expect(text.contains("40"))
    }

    @Test("A negative duration is clamped rather than shown")
    func negativeIsClamped() {
        L10n.use(languageTag: "en")
        defer { L10n.use(languageTag: nil) }
        #expect(!Formatters.duration(minutes: -30).contains("-"))
    }
}
