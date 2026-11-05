import Foundation
import Testing
@testable import Odivrelo

/// What someone is offered before they have typed anything.
@Suite("Place suggestions")
struct PlaceRankingTests {
    private func place(
        _ name: String,
        kind: PlaceKind = .stopPlace,
        bays: Int = 0,
        coverage: CoverageState = .covered,
        operators: [String] = ["ktel"]
    ) -> Place {
        Place(
            id: "ks_\(name)",
            kind: kind,
            name: LocalisedText(el: name, en: name, sq: name),
            parentId: nil,
            municipality: nil,
            latitude: nil,
            longitude: nil,
            coordinateStatus: "valid",
            boardingPointCount: bays,
            operatorIds: operators,
            coverage: coverage
        )
    }

    /// The defect this ranking exists for. The release's place pack is in name
    /// order and the KTEL source data opens with a hyphen, exclamation marks
    /// and postal codes, almost none of which have service.
    @Test("Source-data noise at the head of the pack is not what gets offered")
    func noiseIsNotOffered() {
        let pack = [
            place("-", coverage: .notCovered),
            place("!!!!!!!!!!!!!!!!!!!!!!!!!!", coverage: .notCovered),
            place("1 ΓΥΜΝΑΣΙΟ", coverage: .notCovered),
            place("11.500", kind: .stop, bays: 0),
            place("21 ΧΛΜ Δ/Σ", kind: .stop, bays: 0),
            place("TRIPOLI", bays: 75),
            place("KERKYRA", bays: 54),
            place("KIATO", bays: 49),
        ]

        let offered = PlaceRanking.suggestions(from: pack, limit: 3).map(\.id)

        #expect(offered == ["ks_TRIPOLI", "ks_KERKYRA", "ks_KIATO"])
    }

    @Test("Nothing without service is ever offered")
    func onlyServedPlaces() {
        let pack = [
            place("BUSY BUT DEAD", bays: 900, coverage: .notCovered),
            place("QUIET AND SERVED", bays: 1),
        ]

        let offered = PlaceRanking.suggestions(from: pack, limit: 10)

        #expect(offered.map(\.id) == ["ks_QUIET AND SERVED"])
    }

    /// A terminal is what someone names when they say where they are going,
    /// and its stops hang beneath it.
    @Test("A terminal comes before a single stop")
    func terminalsLead() {
        let pack = [
            place("A STOP", kind: .stop, bays: 40),
            place("A TERMINAL", kind: .stopPlace, bays: 2),
        ]

        #expect(PlaceRanking.suggestions(from: pack, limit: 2).first?.id == "ks_A TERMINAL")
    }

    /// Ranking reads the whole place table, because the served places are
    /// spread through the pack rather than gathered at its front. That is a
    /// much larger read than the sixty rows the picker used to ask for, so it
    /// is worth knowing what the sorting itself costs on a release-sized list.
    @Test("Ranking a release-sized list is not slow")
    func ranksARealisticListQuickly() {
        // The release carries 19,139 places and 959 of them have service.
        let pack = (1...19_139).map {
            place(
                "P\($0)",
                kind: $0 % 2 == 0 ? .stopPlace : .stop,
                bays: $0 % 80,
                coverage: $0 % 20 == 0 ? .covered : .notCovered
            )
        }

        let started = Date()
        let offered = PlaceRanking.suggestions(from: pack, limit: 60)
        let elapsed = Date().timeIntervalSince(started)

        #expect(offered.count == 60)
        #expect(elapsed < 0.5, "ranking took \(elapsed)s")
    }

    @Test("The limit is honoured")
    func limitHonoured() {
        let pack = (1...30).map { place("P\($0)", bays: $0) }

        #expect(PlaceRanking.suggestions(from: pack, limit: 12).count == 12)
    }
}
