import SwiftUI

// The Odivrelo component set. Every colour, space, radius and duration comes
// from `Theme`, which is generated from design/tokens/odivrelo.tokens.json.
// Features never reach for a primitive colour.

// MARK: - Tone

/// A semantic tone. Important states always carry an icon and text as well as
/// a colour, so colour is never the only signal.
public enum Tone: Sendable, Equatable {
    case neutral, info, success, warning, error, brand

    var surface: Color {
        switch self {
        case .neutral: Theme.Palette.surfaceMuted
        case .info: Theme.Palette.infoSurface
        case .success: Theme.Palette.successSurface
        case .warning: Theme.Palette.warningSurface
        case .error: Theme.Palette.errorSurface
        case .brand: Theme.Palette.surfaceMuted
        }
    }

    var text: Color {
        switch self {
        case .neutral: Theme.Palette.textSecondary
        case .info: Theme.Palette.infoText
        case .success: Theme.Palette.successText
        case .warning: Theme.Palette.warningText
        case .error: Theme.Palette.errorText
        case .brand: Theme.Palette.primary
        }
    }
}

// MARK: - Card

/// The standard raised surface: tonal, bordered, with a restrained shadow.
public struct OdivreloCard<Content: View>: View {
    private let content: Content

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    public var body: some View {
        content
            .padding(Theme.Space.medium)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.Palette.surface, in: .rect(cornerRadius: Theme.Radius.large))
            .overlay {
                RoundedRectangle(cornerRadius: Theme.Radius.large)
                    .stroke(Theme.Palette.border, lineWidth: 1)
            }
    }
}

// MARK: - Badge

/// A short status label. The icon carries the same meaning as the colour, so
/// the badge still reads with colour vision differences or in grayscale.
public struct StatusBadge: View {
    private let text: String
    private let systemImage: String
    private let tone: Tone

    public init(_ text: String, systemImage: String, tone: Tone) {
        self.text = text
        self.systemImage = systemImage
        self.tone = tone
    }

    public var body: some View {
        Label {
            Text(text)
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.leading)
        } icon: {
            Image(systemName: systemImage)
        }
        .labelStyle(.titleAndIcon)
        .font(.caption.weight(.semibold))
        .foregroundStyle(tone.text)
        .padding(.horizontal, Theme.Space.xSmall)
        .padding(.vertical, Theme.Space.xxSmall)
        .background(tone.surface, in: .rect(cornerRadius: Theme.Radius.small))
        .accessibilityElement(children: .combine)
    }
}

/// A row of badges that wraps rather than truncating, so a long Greek or
/// Albanian label at an accessibility text size still reads in full.
public struct BadgeFlow: Layout {
    private let spacing: CGFloat

    public init(spacing: CGFloat = Theme.Space.xSmall) {
        self.spacing = spacing
    }

    /// The size each badge takes, never wider than the container.
    ///
    /// A badge whose natural width exceeds the row is re-measured against the
    /// full width so its text wraps inside the card, rather than being laid out
    /// at its natural width and overflowing.
    private func sizes(_ subviews: Subviews, maxWidth: CGFloat) -> [CGSize] {
        subviews.map { subview in
            let natural = subview.sizeThatFits(.unspecified)
            guard maxWidth.isFinite, natural.width > maxWidth else { return natural }
            return subview.sizeThatFits(ProposedViewSize(width: maxWidth, height: nil))
        }
    }

    public func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        let measured = sizes(subviews, maxWidth: maxWidth)

        var x: CGFloat = 0
        var rowHeight: CGFloat = 0
        var total: CGFloat = 0
        var widest: CGFloat = 0

        for size in measured {
            if x > 0, x + spacing + size.width > maxWidth {
                total += rowHeight + spacing
                x = 0
                rowHeight = 0
            }
            x += (x > 0 ? spacing : 0) + size.width
            widest = max(widest, x)
            rowHeight = max(rowHeight, size.height)
        }
        total += rowHeight
        return CGSize(width: maxWidth.isFinite ? maxWidth : widest, height: total)
    }

    public func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let measured = sizes(subviews, maxWidth: bounds.width)
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for (index, subview) in subviews.enumerated() {
            let size = measured[index]
            if x > bounds.minX, x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            subview.place(
                at: CGPoint(x: x, y: y),
                proposal: ProposedViewSize(width: min(size.width, bounds.width), height: size.height)
            )
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

// MARK: - Demonstration notice

/// The persistent demonstration-data notice.
///
/// It appears whenever the release's `dataMode` is `demo`, on every screen that
/// shows data, and it cannot be dismissed. Flipping the release to `real`
/// removes it and changes nothing else.
public struct DemoDataNotice: View {
    public init() {}

    public var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.Space.small) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(Theme.Palette.warningText)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
                Text(L10n.demoTitle)
                    .font(.subheadline.weight(.bold))
                Text(L10n.demoBody)
                    .font(.footnote)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(Theme.Palette.warningText)
        }
        .padding(Theme.Space.small)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Palette.warningSurface, in: .rect(cornerRadius: Theme.Radius.medium))
        .overlay {
            RoundedRectangle(cornerRadius: Theme.Radius.medium)
                .stroke(Theme.Palette.warningText.opacity(0.35), lineWidth: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(L10n.demoAccessibilityLabel). \(L10n.demoBody)")
        .accessibilityAddTraits(.isStaticText)
    }
}

// MARK: - States

/// The shared presentation for loading, empty and failed states. Every screen
/// uses it so no screen can quietly ship without one.
public struct StateMessageView: View {
    public enum Kind: Equatable, Sendable {
        case loading
        case empty(systemImage: String)
        case failure(systemImage: String)
    }

    private let kind: Kind
    private let title: String
    private let message: String?
    private let retry: (() -> Void)?
    private let retryTitle: String

    public init(
        kind: Kind,
        title: String,
        message: String? = nil,
        retryTitle: String = L10n.commonRetry,
        retry: (() -> Void)? = nil
    ) {
        self.kind = kind
        self.title = title
        self.message = message
        self.retryTitle = retryTitle
        self.retry = retry
    }

    public var body: some View {
        VStack(spacing: Theme.Space.small) {
            switch kind {
            case .loading:
                ProgressView()
                    .controlSize(.large)
            case let .empty(systemImage), let .failure(systemImage):
                Image(systemName: systemImage)
                    .font(.system(size: 34, weight: .regular))
                    .foregroundStyle(tint)
                    .accessibilityHidden(true)
            }

            Text(title)
                .font(.headline)
                .multilineTextAlignment(.center)
                .foregroundStyle(Theme.Palette.textPrimary)

            if let message {
                Text(message)
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if let retry {
                Button(retryTitle, action: retry)
                    .buttonStyle(OdivreloSecondaryButtonStyle())
                    .padding(.top, Theme.Space.xSmall)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Theme.Space.xLarge)
        .padding(.horizontal, Theme.Space.medium)
        .accessibilityElement(children: .contain)
    }

    private var tint: Color {
        switch kind {
        case .failure: Theme.Palette.errorText
        default: Theme.Palette.textSecondary
        }
    }
}

// MARK: - Buttons

public struct OdivreloPrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(Theme.Palette.onPrimary)
            .frame(maxWidth: .infinity)
            .frame(minHeight: Theme.Size.control)
            .padding(.horizontal, Theme.Space.medium)
            .background(
                configuration.isPressed ? Theme.Palette.primaryPressed : Theme.Palette.primary,
                in: .rect(cornerRadius: Theme.Radius.medium)
            )
            .opacity(isEnabled ? 1 : 0.45)
            .contentShape(.rect)
    }
}

public struct OdivreloSecondaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(Theme.Palette.primary)
            .frame(minHeight: Theme.Size.touchMinimum)
            .padding(.horizontal, Theme.Space.medium)
            .background(
                configuration.isPressed ? Theme.Palette.surfaceMuted : Theme.Palette.surface,
                in: .rect(cornerRadius: Theme.Radius.medium)
            )
            .overlay {
                RoundedRectangle(cornerRadius: Theme.Radius.medium)
                    .stroke(Theme.Palette.primary.opacity(0.5), lineWidth: 1)
            }
            .opacity(isEnabled ? 1 : 0.45)
            .contentShape(.rect)
    }
}

// MARK: - Structure

public struct SectionCard<Content: View>: View {
    private let title: String
    private let systemImage: String?
    private let content: Content

    public init(_ title: String, systemImage: String? = nil, @ViewBuilder content: () -> Content) {
        self.title = title
        self.systemImage = systemImage
        self.content = content()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.small) {
            HStack(spacing: Theme.Space.xSmall) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .foregroundStyle(Theme.Palette.primary)
                        .accessibilityHidden(true)
                }
                Text(title)
            }
            .font(.headline)
            .foregroundStyle(Theme.Palette.textPrimary)
            .accessibilityAddTraits(.isHeader)

            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Theme.Space.medium)
        .background(Theme.Palette.surface, in: .rect(cornerRadius: Theme.Radius.large))
        .overlay {
            RoundedRectangle(cornerRadius: Theme.Radius.large)
                .stroke(Theme.Palette.border, lineWidth: 1)
        }
    }
}

/// A label and value on one line, wrapping to two when the text is large.
public struct LabelledValue: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private let label: String
    private let value: String
    private let valueIsMonospaced: Bool
    private let figure: String?

    public init(_ label: String, value: String, monospaced: Bool = false) {
        self.label = label
        self.value = value
        self.valueIsMonospaced = monospaced
        self.figure = nil
    }

    /// A row whose value is a figure followed by a name.
    ///
    /// The figure is set as data, in tabular mono, and the name stays in the
    /// sans family. They cannot be one string: a place name in a monospaced
    /// face reads badly at every size, and in Greek and Albanian it is long.
    public init(_ label: String, figure: String, value: String) {
        self.label = label
        self.value = value
        self.valueIsMonospaced = false
        self.figure = figure
    }

    public var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline) {
                labelText
                Spacer(minLength: Theme.Space.small)
                valueText.multilineTextAlignment(.trailing)
            }
            VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
                labelText
                valueText
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(label)
        .accessibilityValue(value)
    }

    private var labelText: some View {
        Text(label)
            .font(.subheadline)
            .foregroundStyle(Theme.Palette.textSecondary)
    }

    private var valueText: some View {
        Group {
            if let figure {
                // Two runs in one line: the figure in tabular mono so a column
                // of times aligns, the name in the reading face.
                Text(figure)
                    .font(.subheadline.monospaced())
                    + Text(verbatim: "  ")
                    + Text(value)
                    .font(.subheadline.weight(.medium))
            } else {
                Text(value)
                    .font(valueIsMonospaced ? .subheadline.monospaced() : .subheadline.weight(.medium))
            }
        }
        .foregroundStyle(Theme.Palette.textPrimary)
        .fixedSize(horizontal: false, vertical: true)
    }
}

// MARK: - Brand mark

public struct BrandMarkView: View {
    private let size: CGFloat

    public init(size: CGFloat = 44) {
        self.size = size
    }

    public var body: some View {
        Image("BrandMark")
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityLabel(L10n.a11yBrandMark)
    }
}

/// The wordmark: the shared mark beside the product name.
///
/// The name is live text rather than a rasterised image, so it stays sharp at
/// any size, follows Dynamic Type, and is read correctly by a screen reader.
public struct WordmarkView: View {
    @ScaledMetric(relativeTo: .largeTitle) private var markSize: CGFloat = 36
    private let font: Font

    public init(font: Font = .largeTitle.weight(.bold)) {
        self.font = font
    }

    public var body: some View {
        HStack(spacing: Theme.Space.xSmall) {
            Image("BrandMark")
                .resizable()
                .scaledToFit()
                .frame(width: markSize, height: markSize)
                .accessibilityHidden(true)
            Text(Brand.name)
                .font(font)
                .foregroundStyle(Theme.Palette.textPrimary)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Brand.name)
    }
}

// MARK: - Layout helpers

public extension View {
    /// Constrains a column to a readable width inside the real container and
    /// applies the side padding the container can afford.
    func readableColumn(_ geometry: WindowGeometry) -> some View {
        frame(maxWidth: LayoutResolver.contentWidth(for: geometry))
            .frame(maxWidth: .infinity)
            .padding(.horizontal, LayoutResolver.sidePadding(for: geometry))
    }

    /// Keeps content clear of any region the system reserved inside the window.
    /// With no reserved regions this is the identity, which is what every
    /// current device reports.
    func avoidingReservedRegions(_ geometry: WindowGeometry) -> some View {
        modifier(ReservedRegionInsets(geometry: geometry))
    }
}

private struct ReservedRegionInsets: ViewModifier {
    let geometry: WindowGeometry

    func body(content: Content) -> some View {
        guard let region = geometry.splittingRegion else { return AnyView(content) }
        // Content stays on the larger side of a splitting region rather than
        // straddling it.
        switch geometry.asymmetry {
        case let .trailingLarger(leading, _):
            return AnyView(content.padding(.leading, max(0, region.rect.maxX - leading)))
        default:
            return AnyView(content.padding(.trailing, max(0, geometry.size.width - region.rect.minX)))
        }
    }
}

// MARK: - Provenance rail

/// How fresh an asserted fact is. The design system keeps this separate from
/// `Tone`, because "checked recently" is not the same claim as "success" and
/// "past the freshness window" is not the same claim as "error".
public enum RailTint: Sendable, Equatable {
    case fresh, aging, stale

    var color: Color {
        switch self {
        case .fresh: Theme.Palette.railFresh
        case .aging: Theme.Palette.railAging
        case .stale: Theme.Palette.railStale
        }
    }
}

/// The signature element: a rule on the leading edge of a block that asserts a
/// fact, tinted by how fresh that fact is.
///
/// The rail is a fast read, never the only read. The caller always pairs it
/// with the checked-at date in text, which is what a screen reader announces,
/// so the rail itself is hidden from accessibility.
public struct ProvenanceRail<Content: View>: View {
    private let tint: RailTint
    private let content: Content

    public init(_ tint: RailTint, @ViewBuilder content: () -> Content) {
        self.tint = tint
        self.content = content()
    }

    public var body: some View {
        content
            .padding(.leading, Theme.Space.small + Self.width)
            // An overlay matches the content's height exactly, so the rail
            // always spans the whole block it marks, at any text size.
            .overlay(alignment: .leading) {
                Capsule(style: .continuous)
                    .fill(tint.color)
                    .frame(width: Self.width)
                    .accessibilityHidden(true)
            }
    }

    private static var width: CGFloat { 3 }
}

// MARK: - Bay plate

/// The boarding bay, set the way a terminal sets it: a square-cornered plate in
/// tabular figures. The loudest datum on a journey screen, because it is what a
/// person is looking for while standing in a concourse.
///
/// There is no placeholder bay. A caller with no reviewed boarding point shows
/// the honest unavailable state instead of an empty plate.
public struct BayPlate: View {
    private let label: String
    private let value: String

    public init(label: String, value: String) {
        self.label = label
        self.value = value
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Theme.Palette.textSecondary)
            Text(value)
                // A relative size, so the plate grows with Dynamic Type rather
                // than staying fixed while everything around it scales.
                .font(.system(.title, design: .monospaced).weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(Theme.Palette.primaryInk)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .padding(.horizontal, Theme.Space.small)
        .padding(.vertical, Theme.Space.xSmall)
        .background(Theme.Palette.primarySoft, in: .rect(cornerRadius: Theme.Radius.plate))
        .overlay {
            RoundedRectangle(cornerRadius: Theme.Radius.plate)
                .stroke(Theme.Palette.border, lineWidth: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label), \(value)")
    }
}

// MARK: - The functional layer

/// Glass for the layer of floating controls and navigation.
///
/// Apple's model has exactly two layers and glass belongs only to this one, so
/// nothing in the content layer may use this modifier. Reduce Transparency gets
/// an opaque surface, and builds below the glass API fall back to a standard
/// material rather than imitating it.
public struct FunctionalGlass: ViewModifier {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    private let cornerRadius: CGFloat

    public init(cornerRadius: CGFloat) {
        self.cornerRadius = cornerRadius
    }

    public func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)

        if reduceTransparency {
            content
                .background(Theme.Palette.surface, in: shape)
                .overlay { shape.stroke(Theme.Palette.border, lineWidth: 1) }
        } else if #available(iOS 26.0, *) {
            content.glassEffect(.regular, in: shape)
        } else {
            content
                .background(.regularMaterial, in: shape)
                .overlay {
                    shape.stroke(
                        Theme.Palette.border.opacity(Theme.Material.glassBorderOpacity),
                        lineWidth: 1
                    )
                }
        }
    }
}

public extension View {
    /// Applies the functional-layer material. Only for floating controls and
    /// navigation, never for content.
    func functionalGlass(cornerRadius: CGFloat = Theme.Radius.panel) -> some View {
        modifier(FunctionalGlass(cornerRadius: cornerRadius))
    }
}
