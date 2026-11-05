import Foundation

/**
 What to offer someone who has not typed anything yet.

 The release carries 19,139 places and 959 of them have any service at all.
 They arrive in the pack in name order, and the KTEL source data begins with
 a hyphen, two rows of exclamation marks, a school, and six postal codes. An
 empty query therefore opens on a list that reads as broken and is almost
 entirely dead ends: of the first sixty places, five have service, and those
 five are called "11.500", "1ο ΓΥΜΝΑΣΙΟ", "21 KM" and "21 ΧΛΜ Δ/Σ".

 Ordering by how much service a place actually has answers it. The terminals
 with the most boarding points are Tripoli, Kerkyra, Kiato, Trikala, Kavala,
 Xanthi, Lamia and Agrinio, which are the places a traveller is looking for.

 This is only for the default list. Once someone types, matches are shown as
 they come, not covered ones: a stop with no service is a real answer to
 "where is this stop", and the coverage badge says so on the row.
 */
enum PlaceRanking {
    /// Somewhere a coach actually leaves from, most-served first.
    ///
    /// Terminals before single stops, because a terminal is what someone names
    /// when they say where they are going, and its stops hang beneath it.
    static func suggestions(from places: [Place], limit: Int) -> [Place] {
        places
            .filter { $0.coverage == .covered }
            .sorted { lhs, rhs in
                if (lhs.kind == .stopPlace) != (rhs.kind == .stopPlace) {
                    return lhs.kind == .stopPlace
                }
                if lhs.boardingPointCount != rhs.boardingPointCount {
                    return lhs.boardingPointCount > rhs.boardingPointCount
                }
                return lhs.operatorIds.count > rhs.operatorIds.count
            }
            .prefix(limit)
            .map { $0 }
    }

    /// How many places to read before ranking them.
    ///
    /// Ranking needs more than it shows, because the served places are spread
    /// through the pack rather than gathered at its front. This reads the whole
    /// place list, which is the only way to find them, and it happens once when
    /// a picker opens.
    static let scanLimit = 20_000
}
