import SwiftUI

/// Everything the interface is allowed to use when deciding a layout.
///
/// It is built from the *actual* container the view was given, not from a
/// device model, a screen bounds, a hardcoded fold position or an assumed
/// full-screen width. On iPad that container is the Split View, Slide Over or
/// Stage Manager window; on a foldable it is whatever the system hands the
/// scene in the current posture; in a resized window it is the resized size.
///
/// `reserved` carries any region of the container the system has told us not to
/// place content under. See `ReservedRegions` for how that is obtained and what
/// this build can and cannot know.
public struct WindowGeometry: Equatable, Sendable {
    /// The container's size in points, safe areas included.
    public var size: CGSize
    /// The container's safe-area insets.
    public var safeArea: EdgeInsets
    /// Regions inside the container that content must avoid.
    public var reserved: [ReservedRegion]
    /// The height the keyboard currently covers, in points.
    public var keyboardInset: CGFloat
    public var horizontalSizeClass: UserInterfaceSizeClass?
    public var verticalSizeClass: UserInterfaceSizeClass?
    /// The active Dynamic Type size, which changes how much fits in a column.
    public var dynamicTypeSize: DynamicTypeSize

    public init(
        size: CGSize,
        safeArea: EdgeInsets,
        reserved: [ReservedRegion] = [],
        keyboardInset: CGFloat = 0,
        horizontalSizeClass: UserInterfaceSizeClass? = nil,
        verticalSizeClass: UserInterfaceSizeClass? = nil,
        dynamicTypeSize: DynamicTypeSize = .large
    ) {
        self.size = size
        self.safeArea = safeArea
        self.reserved = reserved
        self.keyboardInset = keyboardInset
        self.horizontalSizeClass = horizontalSizeClass
        self.verticalSizeClass = verticalSizeClass
        self.dynamicTypeSize = dynamicTypeSize
    }

    /// The width actually available for content: container width less safe
    /// areas, less any reserved region that spans the full height. A reserved
    /// strip that only covers part of the height does not reduce the usable
    /// width; it is avoided by the views that would otherwise sit under it.
    public var usableWidth: CGFloat {
        let base = size.width - safeArea.leading - safeArea.trailing
        let spanning = reserved
            .filter { $0.rect.height >= size.height * 0.92 }
            .map(\.rect.width)
            .reduce(0, +)
        return max(0, base - spanning)
    }

    public var usableHeight: CGFloat {
        max(0, size.height - safeArea.top - safeArea.bottom - keyboardInset)
    }

    /// True when the keyboard currently covers part of the container.
    public var keyboardIsVisible: Bool { keyboardInset > 1 }

    /// The reserved region that splits the container into two usable panels,
    /// when one exists. A region qualifies when it runs the full height and
    /// leaves useful width on both sides.
    public var splittingRegion: ReservedRegion? {
        reserved.first { region in
            region.rect.height >= size.height * 0.92
                && region.rect.minX > size.width * 0.15
                && region.rect.maxX < size.width * 0.85
        }
    }

    /// Whether the usable area is asymmetric: one side of a splitting region is
    /// meaningfully larger than the other. Layouts put the primary content on
    /// the larger side.
    public var asymmetry: Asymmetry {
        guard let region = splittingRegion else { return .none }
        let leading = region.rect.minX - safeArea.leading
        let trailing = size.width - safeArea.trailing - region.rect.maxX
        if abs(leading - trailing) < 24 { return .balanced }
        return leading > trailing ? .leadingLarger(leading: leading, trailing: trailing)
                                  : .trailingLarger(leading: leading, trailing: trailing)
    }

    public enum Asymmetry: Equatable, Sendable {
        case none
        case balanced
        case leadingLarger(leading: CGFloat, trailing: CGFloat)
        case trailingLarger(leading: CGFloat, trailing: CGFloat)
    }
}

/// A region of the window that content must not be placed under.
///
/// On a system that reports occluded or reserved areas the values come from the
/// system. On every other system the list is empty and layout falls back to
/// pure geometry, which is why nothing here depends on a device name.
public struct ReservedRegion: Equatable, Identifiable, Sendable {
    public enum Kind: String, Sendable {
        /// Reported by the system as unavailable for content.
        case systemReported
        /// Inferred from the container's own geometry when the system does not
        /// report regions. Treated as advisory: content is kept clear of it,
        /// but no behaviour depends on it being correct.
        case inferred
    }

    public var id: String
    public var rect: CGRect
    public var kind: Kind

    public init(id: String, rect: CGRect, kind: Kind) {
        self.id = id
        self.rect = rect
        self.kind = kind
    }
}

// MARK: - Layout decision

/// The layout the interface should present for a given window.
///
/// The thresholds are reading widths, not device sizes. A regular-width iPad in
/// a narrow Slide Over column gets the compact layout because the column is
/// narrow, which is the correct answer.
public enum LayoutMode: String, Equatable, Sendable {
    /// One column, `NavigationStack`, detail pushed.
    case singleColumn
    /// Two columns, `NavigationSplitView`: list beside detail.
    case twoColumn
    /// Three columns: list, detail and a persistent map or supporting pane.
    case threeColumn
}

public enum LayoutResolver {
    /// The narrowest column a pane may be.
    ///
    /// The whole interface is usable at 320 points, which is the narrowest
    /// iPhone the deployment target supports, so a pane that wide is readable
    /// by the same standard. Anything narrower gets one column instead. This is
    /// a reading width, not a device size: a two-thirds iPad Split View pane
    /// (694 points) splits, a half pane (507) does not, and a Slide Over column
    /// (320) does not.
    public static let minimumPaneWidth: CGFloat = 320

    /// The point above which a third pane earns its place.
    public static let threeColumnWidth: CGFloat = 1_180

    public static func mode(for geometry: WindowGeometry) -> LayoutMode {
        // An accessibility text size needs the whole width for one column.
        if geometry.dynamicTypeSize >= .accessibility3 {
            return .singleColumn
        }

        let width = geometry.usableWidth

        // A window split by a reserved region: each side must be wide enough on
        // its own, otherwise the split would produce two unusable columns.
        if let region = geometry.splittingRegion {
            let leading = region.rect.minX - geometry.safeArea.leading
            let trailing = geometry.size.width - geometry.safeArea.trailing - region.rect.maxX
            if min(leading, trailing) >= minimumPaneWidth {
                return .twoColumn
            }
            return .singleColumn
        }

        if geometry.horizontalSizeClass == .compact { return .singleColumn }

        if width >= threeColumnWidth { return .threeColumn }
        if width >= minimumPaneWidth * 2 + Theme.Space.medium { return .twoColumn }
        return .singleColumn
    }

    /// The width a single readable column should take inside the given window.
    public static func contentWidth(for geometry: WindowGeometry) -> CGFloat {
        min(geometry.usableWidth, Theme.Size.contentMaximum)
    }

    /// The side padding for the main content, from the design system, reduced
    /// when the usable width is too small to afford it.
    public static func sidePadding(for geometry: WindowGeometry) -> CGFloat {
        geometry.usableWidth < 360 ? Theme.Space.small : Theme.Space.mediumPlus
    }
}
