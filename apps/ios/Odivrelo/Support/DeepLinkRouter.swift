import Foundation
import os

/// Resolves the two link surfaces Odivrelo accepts:
///
/// * Universal Links on `https://odivrelo.peterdsp.dev`
/// * the custom scheme `odivrelo://`
///
/// Both are validated, not trusted. A host that is not the controlled domain,
/// a scheme that is not the product's own, an unknown path, a malformed
/// identifier or a service date in the past all produce a stated outcome rather
/// than a blank screen or a wrong one.
public enum DeepLinkRouter {
    public enum Link: Equatable, Sendable {
        case search(originId: String?, destinationId: String?, serviceDate: ServiceDate?)
        case journey(id: String, serviceDate: ServiceDate)
        case operatorPage(id: String)
        case stop(id: String, serviceDate: ServiceDate?)
        case offline
        case trips
        case settings
        case coverage
        case sources
    }

    private static let log = Logger(subsystem: Brand.bundleIdentifier, category: "deeplink")

    /// Parses a link without touching application state, so it is testable on
    /// its own.
    /// `today` comes from the core, which is what decides whether a link naming
    /// a service date has expired.
    public static func parse(_ url: URL, today: ServiceDate) -> Result<Link, UnresolvedLink.Reason> {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return .failure(.unknown)
        }

        let path: [String]
        switch components.scheme?.lowercased() {
        case "https":
            // Only the domain this product actually controls is accepted.
            guard components.host?.lowercased() == Brand.domain.lowercased() else {
                return .failure(.unknown)
            }
            path = components.path.split(separator: "/").map(String.init)
        case Brand.urlScheme:
            // odivrelo://journey/<id>?date=…  — the host is the first segment.
            var segments: [String] = []
            if let host = components.host, !host.isEmpty { segments.append(host) }
            segments.append(contentsOf: components.path.split(separator: "/").map(String.init))
            path = segments
        default:
            return .failure(.unknown)
        }

        let query = Dictionary(
            (components.queryItems ?? []).compactMap { item -> (String, String)? in
                guard let value = item.value, !value.isEmpty else { return nil }
                return (item.name, value)
            },
            uniquingKeysWith: { first, _ in first }
        )

        func date(_ key: String = "date") -> ServiceDate? {
            query[key].flatMap(ServiceDate.init(iso:))
        }

        func expiredIfPast(_ value: ServiceDate) -> Result<Link, UnresolvedLink.Reason>? {
            value < today ? .failure(.expired(value)) : nil
        }

        guard let first = path.first?.lowercased() else {
            return .success(.search(originId: nil, destinationId: nil, serviceDate: date()))
        }

        switch first {
        case "search", "journeys":
            if let value = date(), let expired = expiredIfPast(value) { return expired }
            return .success(.search(
                originId: identifier(query["origin"] ?? query["from"]),
                destinationId: identifier(query["destination"] ?? query["to"]),
                serviceDate: date()
            ))

        case "journey":
            guard path.count >= 2, let id = identifier(path[1]) else { return .failure(.unknown) }
            guard let value = date() else { return .failure(.unknown) }
            if let expired = expiredIfPast(value) { return expired }
            return .success(.journey(id: id, serviceDate: value))

        case "operator", "operators":
            guard path.count >= 2, let id = identifier(path[1]) else { return .failure(.unknown) }
            return .success(.operatorPage(id: id))

        case "stop", "stops":
            guard path.count >= 2, let id = identifier(path[1]) else { return .failure(.unknown) }
            if let value = date(), let expired = expiredIfPast(value) { return expired }
            return .success(.stop(id: id, serviceDate: date()))

        case "offline": return .success(.offline)
        case "trips", "saved": return .success(.trips)
        case "settings": return .success(.settings)
        case "coverage": return .success(.coverage)
        case "sources": return .success(.sources)
        default: return .failure(.unknown)
        }
    }

    /// Identifiers are opaque per the contract, so they are not parsed. They are
    /// only checked for a shape that cannot smuggle a path or a control
    /// character into a later request.
    ///
    /// `URLComponents.path` percent-decodes, so `..%2F..%2Fetc` arrives as
    /// `../../etc` and splits into `..` segments. A dot is legitimate inside an
    /// identifier such as `place.kentro-terminal`, so the check is not "no
    /// dots": it is that a segment must not be a relative path component and
    /// must not contain a run of dots that could climb a path.
    private static func identifier(_ raw: String?) -> String? {
        guard let raw, !raw.isEmpty, raw.count <= 128 else { return nil }

        // A relative path component is never an identifier.
        guard raw != ".", raw != ".." else { return nil }
        // Two consecutive dots can only be an attempt to climb.
        guard !raw.contains("..") else { return nil }
        // A separator must not survive decoding, in either direction.
        guard !raw.contains("/"), !raw.contains("\\") else { return nil }

        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: ".-_:"))
        guard raw.unicodeScalars.allSatisfy(allowed.contains) else { return nil }
        return raw
    }

    @MainActor
    public static func handle(_ url: URL, in model: AppModel) {
        switch parse(url, today: model.today) {
        case let .success(link):
            apply(link, in: model)
        case let .failure(reason):
            log.notice("link not resolved")
            model.route.unresolvedLink = UnresolvedLink(url: url, reason: reason)
        }
    }

    @MainActor
    public static func apply(_ link: Link, in model: AppModel) {
        switch link {
        case let .search(originId, destinationId, serviceDate):
            model.session.selectedTab = .search
            if let originId { model.session.originId = originId }
            if let destinationId { model.session.destinationId = destinationId }
            if let serviceDate { model.session.serviceDate = serviceDate }
            model.route.searchPath = []
        case let .journey(id, serviceDate):
            model.session.selectedTab = .search
            model.session.selectedJourneyId = id
            model.session.serviceDate = serviceDate
            model.route.searchPath = [.journey(id: id, serviceDate: serviceDate)]
        case let .operatorPage(id):
            model.route.setPath(
                model.route.path(for: model.session.selectedTab) + [.operatorPage(id: id)],
                for: model.session.selectedTab
            )
        case let .stop(id, serviceDate):
            let date = serviceDate ?? model.session.serviceDate
            model.route.setPath(
                model.route.path(for: model.session.selectedTab) + [.stop(id: id, serviceDate: date)],
                for: model.session.selectedTab
            )
        case .offline:
            model.session.selectedTab = .offline
        case .trips:
            model.session.selectedTab = .trips
        case .settings:
            model.session.selectedTab = .settings
        case .coverage:
            model.session.selectedTab = .settings
            model.route.settingsPath = [.coverage]
        case .sources:
            model.session.selectedTab = .settings
            model.route.settingsPath = [.sources]
        }
        model.persistSession()
    }
}
