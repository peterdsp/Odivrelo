import MapKit
import SwiftUI

/// The route on a MapKit map, with the stop list above it as the complete
/// alternative.
///
/// The geometry's own confidence decides how it is drawn: anything short of a
/// reviewed shape is dashed and labelled, because a straight line between
/// ordered stops is not the road a coach takes and must never look like one.
struct RouteMapSection: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let detail: JourneyDetail

    @State private var camera: MapCameraPosition = .automatic

    var body: some View {
        SectionCard(L10n.detailMap, systemImage: "map") {
            VStack(alignment: .leading, spacing: Theme.Space.small) {
                if let geometry = detail.journey.geometry,
                   geometry.confidence.isDrawable,
                   geometry.coordinates.count >= 2 {
                    map(geometry)
                    BadgeFlow {
                        GeometryConfidenceBadge(geometry.confidence)
                        StatusBadge(
                            geometry.attribution ?? L10n.commonNotStated,
                            systemImage: "c.circle",
                            tone: .neutral
                        )
                    }
                    Text(geometry.method ?? L10n.commonNotStated)
                        .font(.caption)
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    StateMessageView(
                        kind: .empty(systemImage: "map"),
                        title: L10n.geometryUnverified,
                        message: L10n.detailStopsListAlternative
                    )
                }

                Text(L10n.detailMapUnavailableOffline)
                    .font(.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    /// Stops carry their own coordinate, resolved by stop id. Drawing them from
    /// the stop list is what makes a marker a real stop and not a bend in the
    /// route line.
    private var locatedStops: [JourneyStop] {
        detail.journey.stops.filter { $0.latitude != nil && $0.longitude != nil }
    }

    private func map(_ geometry: RouteGeometry) -> some View {
        let coordinates = geometry.coordinates.compactMap { pair -> CLLocationCoordinate2D? in
            guard pair.count == 2 else { return nil }
            return CLLocationCoordinate2D(latitude: pair[1], longitude: pair[0])
        }
        let stops = locatedStops
        let stopCoordinates = stops.compactMap { stop -> CLLocationCoordinate2D? in
            guard let latitude = stop.latitude, let longitude = stop.longitude else { return nil }
            return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        }
        let language = model.settings.effectiveLanguageTag

        return Map(position: $camera, interactionModes: [.pan, .zoom]) {
            if geometry.confidence == .reviewed {
                MapPolyline(coordinates: coordinates)
                    .stroke(Theme.Palette.primary, lineWidth: 6)
            } else {
                // Unreviewed geometry is dotted, never a solid confident line.
                MapPolyline(coordinates: coordinates)
                    .stroke(
                        Theme.Palette.primary,
                        style: StrokeStyle(lineWidth: 4, lineCap: .round, dash: [2, 9])
                    )
            }

            // Each stop is placed at its own coordinate, resolved by stop id, and
            // labelled with its real name. The boarded and alighted stops of the
            // selected segment are prominent; stops outside the segment are muted.
            ForEach(stops) { stop in
                if let latitude = stop.latitude, let longitude = stop.longitude {
                    Annotation(
                        stop.name.resolved(for: language),
                        coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
                    ) {
                        stopMarker(for: stop.segmentRole)
                    }
                }
            }
        }
        .mapControlVisibility(.visible)
        .frame(minHeight: 220, maxHeight: 320)
        .clipShape(.rect(cornerRadius: Theme.Radius.medium))
        // The map is decorative for a screen reader: the stop list above holds
        // every fact it shows, so the reader is pointed there instead of being
        // dropped into an unlabelled canvas.
        .accessibilityElement()
        .accessibilityLabel(L10n.detailMap)
        .accessibilityHint(L10n.a11yMapHint)
        .onAppear {
            let framing = stopCoordinates.isEmpty ? coordinates : stopCoordinates
            guard !framing.isEmpty else { return }
            camera = .region(Self.region(covering: framing))
        }
    }

    /// The marker for a stop, styled by its role in the selected segment:
    /// the boarded and alighted stops stand out, stops outside the segment are
    /// muted, exactly as the stop list treats them.
    @ViewBuilder
    private func stopMarker(for role: SegmentRole) -> some View {
        switch role {
        case .board, .alight:
            Image(systemName: role == .board ? "figure.walk.arrival" : "figure.walk.departure")
                .font(.title3)
                .foregroundStyle(Theme.Palette.focus, Theme.Palette.primary)
                .accessibilityHidden(true)
        case .onSegment:
            Circle()
                .fill(Theme.Palette.surface)
                .stroke(Theme.Palette.primary, lineWidth: 3)
                .frame(width: 12, height: 12)
                .accessibilityHidden(true)
        case .beforeBoard, .afterAlight:
            Circle()
                .fill(Theme.Palette.surface)
                .stroke(Theme.Palette.textSecondary, lineWidth: 2)
                .frame(width: 9, height: 9)
                .opacity(0.6)
                .accessibilityHidden(true)
        }
    }

    /// A region that covers every coordinate with a small margin. Nothing here
    /// animates the camera on its own, which also satisfies Reduce Motion.
    static func region(covering coordinates: [CLLocationCoordinate2D]) -> MKCoordinateRegion {
        let latitudes = coordinates.map(\.latitude)
        let longitudes = coordinates.map(\.longitude)
        let minLatitude = latitudes.min() ?? 0
        let maxLatitude = latitudes.max() ?? 0
        let minLongitude = longitudes.min() ?? 0
        let maxLongitude = longitudes.max() ?? 0
        let centre = CLLocationCoordinate2D(
            latitude: (minLatitude + maxLatitude) / 2,
            longitude: (minLongitude + maxLongitude) / 2
        )
        let span = MKCoordinateSpan(
            latitudeDelta: max(0.05, (maxLatitude - minLatitude) * 1.4),
            longitudeDelta: max(0.05, (maxLongitude - minLongitude) * 1.4)
        )
        return MKCoordinateRegion(center: centre, span: span)
    }
}

struct GeometryConfidenceBadge: View {
    private let confidence: GeometryConfidence

    init(_ confidence: GeometryConfidence) {
        self.confidence = confidence
    }

    var body: some View {
        switch confidence {
        case .reviewed:
            StatusBadge(L10n.geometryReviewed, systemImage: "checkmark.shield", tone: .success)
        case .orderedStopsOnly:
            StatusBadge(L10n.geometryOrderedStopsOnly, systemImage: "point.topleft.down.curvedto.point.bottomright.up", tone: .warning)
        case .osmCandidate:
            StatusBadge(L10n.geometryOsmCandidate, systemImage: "questionmark.circle", tone: .warning)
        case .rejected:
            StatusBadge(L10n.geometryRejected, systemImage: "xmark.shield", tone: .error)
        case .unverified:
            StatusBadge(L10n.geometryUnverified, systemImage: "exclamationmark.triangle", tone: .warning)
        }
    }
}
