import SwiftUI

/// Supplies the regions of a window that content must avoid.
///
/// ## What this build can and cannot know
///
/// The installed toolchain is Xcode 27.0 with the iOS 27.0 SDK. That SDK does
/// **not** expose a public API for a foldable's reserved or occluded region:
///
/// * `SwiftUI.ReservedRegion` and
///   `GeometryProxy.reservedRegions(kind:options:layoutDirectionBehavior:)`
///   exist as linker symbols in `SwiftUICore.tbd`, but neither appears in the
///   public `SwiftUI.swiftinterface`, so Swift cannot name them. A typecheck of
///   `proxy.reservedRegions(kind: .all)` against `iphonesimulator27.0` fails
///   with `value of type 'GeometryProxy' has no member 'reservedRegions'`.
/// * `UIViewLayoutRegion` (iOS 26+) covers display-corner adaptation for the
///   safe area, margins and readable content. It reports nothing about a fold.
/// * No UIKit header in this SDK mentions a hinge, a posture or an occluded
///   area, and a documentation search against the installed Xcode returns
///   nothing for iPhone Duo reserved regions.
///
/// So this build takes the honest position: it never guesses. A posture is
/// **not** inferred from an aspect ratio, a screen size or a device model, and
/// `SystemReservedRegionSource` reports nothing until a system API can fill it.
/// Layout therefore runs entirely on the container's real usable geometry, safe
/// areas, Dynamic Type and the keyboard. That is correct on every device
/// including a foldable, and stays correct the moment regions do arrive,
/// because every layout decision already reads `WindowGeometry.reserved`.
///
/// `LayoutResolverTests` drive `LayoutResolver` with explicit geometry
/// fixtures, including asymmetric split windows. Those are supplementary
/// evidence about the resolver, not proof of runtime foldable support.
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
        // Integration point. The iOS 27.0 SDK exposes no public reserved-region
        // API; see the type documentation for exactly what was checked.
        // Reporting nothing is the truthful answer, and the geometry-driven
        // layout handles it correctly.
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
                reserved: regionSource.regions(
                    inContainerOfSize: proxy.size,
                    safeArea: proxy.safeAreaInsets
                ),
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
