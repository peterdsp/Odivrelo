import SwiftUI

/// Supplies the regions of a window that content must avoid.
///
/// ## What this build can know
///
/// The installed toolchain is Xcode 27.1 with the iOS 27.1 SDK, and that SDK
/// *does* expose reserved regions:
///
/// * `GeometryProxy.reservedRegions(kind:)` returns `[SwiftUI.ReservedRegion]`,
///   each carrying `frame`, `kind`, `margins` and `isActive`. The two kinds are
///   `.occlusion`, an area covered by something, and `.division`, an area where
///   content should split into two.
/// * `UIView.reservedRegions(kind:options:)` is the UIKit equivalent, declared
///   in `UIViewReservedRegion.h` and available from iOS 27.1.
///
/// `WindowGeometryReader` reads the SwiftUI API directly from the proxy it
/// already has, because the reading needs a live view hierarchy that this
/// protocol's container-size signature cannot supply. A system that predates
/// the API reports nothing, and layout falls back to pure geometry, which was
/// always correct.
///
/// Note that `frame` already includes `margins`, so the rect is used as given
/// rather than inset again.
///
/// A posture is still never inferred from an aspect ratio, a screen size or a
/// device model. Every layout decision reads `WindowGeometry.reserved`.
///
/// `LayoutResolverTests` drive `LayoutResolver` with explicit geometry
/// fixtures, including asymmetric split windows.
public protocol ReservedRegionSource: Sendable {
    func regions(inContainerOfSize size: CGSize, safeArea: EdgeInsets) -> [ReservedRegion]
}

/// The system-backed source.
///
/// When the SDK begins to vend reserved regions, the single method below is the
/// only place that changes: it gains an `if #available(...)` branch mapping the
/// system values into `ReservedRegion(kind: .systemReported)`.
public struct SystemReservedRegionSource: ReservedRegionSource {
    public init() {}

    public func regions(inContainerOfSize size: CGSize, safeArea: EdgeInsets) -> [ReservedRegion] {
        // A container size is not enough to answer this: the system API reads
        // from a live view. `WindowGeometryReader` performs that read against
        // its own proxy and merges the result, so this stays empty rather than
        // guessing from the size it was handed.
        []
    }
}

/// A source fed from outside, used by geometry fixtures in tests and by the
/// Debug layout inspector. Never installed in a shipping code path.
public struct ExplicitReservedRegionSource: ReservedRegionSource {
    private let fixed: [ReservedRegion]

    public init(_ fixed: [ReservedRegion]) {
        self.fixed = fixed
    }

    public func regions(inContainerOfSize size: CGSize, safeArea: EdgeInsets) -> [ReservedRegion] {
        fixed
    }
}

// MARK: - Environment plumbing

private struct WindowGeometryKey: EnvironmentKey {
    static let defaultValue = WindowGeometry(
        size: CGSize(width: 390, height: 844),
        safeArea: EdgeInsets(top: 59, leading: 0, bottom: 34, trailing: 0)
    )
}

private struct LayoutModeKey: EnvironmentKey {
    static let defaultValue = LayoutMode.singleColumn
}

public extension EnvironmentValues {
    /// The real container the current view was given.
    var windowGeometry: WindowGeometry {
        get { self[WindowGeometryKey.self] }
        set { self[WindowGeometryKey.self] = newValue }
    }

    /// The layout `LayoutResolver` chose for that container.
    var layoutMode: LayoutMode {
        get { self[LayoutModeKey.self] }
        set { self[LayoutModeKey.self] = newValue }
    }
}

/// Carries the container's bottom safe-area inset measured with the keyboard
/// excluded, so the keyboard's own contribution can be separated out.
private struct StableBottomInsetKey: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = max(value, nextValue())
    }
}

/// Measures the actual container and publishes a `WindowGeometry` and a
/// `LayoutMode` into the environment.
///
/// Everything it measures comes from the container it was placed in: the
/// proxy's size and safe-area insets, the size classes, the Dynamic Type size,
/// and the keyboard. There is no `UIScreen`, no device check and no assumption
/// that the scene fills the display, so it is correct in Split View, Slide
/// Over, Stage Manager and any resized or repositioned window.
public struct WindowGeometryReader<Content: View>: View {
    /// What the system reports for this container, falling back to the injected
    /// source on a system without the API.
    static func reservedRegions(
        from proxy: GeometryProxy,
        orFrom source: any ReservedRegionSource
    ) -> [ReservedRegion] {
        if #available(iOS 27.1, *) {
            let system = Self.systemRegions(from: proxy)
            if !system.isEmpty { return system }
        }
        return source.regions(
            inContainerOfSize: proxy.size,
            safeArea: proxy.safeAreaInsets
        )
    }

    /// Maps SwiftUI's reserved regions onto ours. Inactive regions are dropped,
    /// because a fold that is currently flat reserves nothing.
    @available(iOS 27.1, *)
    private static func systemRegions(from proxy: GeometryProxy) -> [ReservedRegion] {
        let kinds: [(SwiftUI.ReservedRegion.Kind, String)] = [
            (.occlusion, "occlusion"),
            (.division, "division"),
        ]
        return kinds.flatMap { kind, name in
            proxy.reservedRegions(kind: kind)
                .enumerated()
                .filter { _, region in region.isActive }
                .map { index, region in
                    // `frame` already includes the region's margins.
                    ReservedRegion(
                        id: "system.\(name).\(index)",
                        rect: region.frame,
                        kind: .systemReported
                    )
                }
        }
    }

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var stableBottomInset: CGFloat = 0

    private let regionSource: any ReservedRegionSource
    private let content: (WindowGeometry, LayoutMode) -> Content

    public init(
        regionSource: any ReservedRegionSource = SystemReservedRegionSource(),
        @ViewBuilder content: @escaping (WindowGeometry, LayoutMode) -> Content
    ) {
        self.regionSource = regionSource
        self.content = content
    }

    public var body: some View {
        GeometryReader { proxy in
            // `proxy.safeAreaInsets.bottom` grows by the keyboard's height when
            // the keyboard is up. The background proxy below ignores the
            // keyboard, so the difference is exactly the covered height.
            let keyboard = max(0, proxy.safeAreaInsets.bottom - stableBottomInset)
            let geometry = WindowGeometry(
                size: proxy.size,
                safeArea: EdgeInsets(
                    top: proxy.safeAreaInsets.top,
                    leading: proxy.safeAreaInsets.leading,
                    bottom: proxy.safeAreaInsets.bottom - keyboard,
                    trailing: proxy.safeAreaInsets.trailing
                ),
                // The system's own answer when it has one, and the injected
                // source otherwise. A fixture source still works on a system
                // that reports nothing, which is what the Debug inspector uses.
                reserved: Self.reservedRegions(from: proxy, orFrom: regionSource),
                keyboardInset: keyboard,
                horizontalSizeClass: horizontalSizeClass,
                verticalSizeClass: verticalSizeClass,
                dynamicTypeSize: dynamicTypeSize
            )
            let mode = LayoutResolver.mode(for: geometry)

            content(geometry, mode)
                .environment(\.windowGeometry, geometry)
                .environment(\.layoutMode, mode)
                .background(alignment: .bottom) {
                    GeometryReader { stable in
                        Color.clear.preference(
                            key: StableBottomInsetKey.self,
                            value: stable.safeAreaInsets.bottom
                        )
                    }
                    .ignoresSafeArea(.keyboard, edges: .bottom)
                    .accessibilityHidden(true)
                }
        }
        .onPreferenceChange(StableBottomInsetKey.self) { value in
            guard abs(value - stableBottomInset) > 0.5 else { return }
            stableBottomInset = value
        }
    }
}
