import SwiftUI

/// Where every fact on the screen came from: source, retrieval time, rights
/// status and licence, plus the correction path.
///
/// This is shown in full rather than behind a link, because a schedule a
/// traveller is about to rely on should carry its own evidence.
struct ProvenanceSection: View {
    @Environment(AppModel.self) private var model

    let entries: [Provenance]
    let correctionUrl: URL?

    @State private var openingCorrection: URL?

    var body: some View {
        SectionCard(L10n.detailProvenance, systemImage: "doc.text.magnifyingglass") {
            VStack(alignment: .leading, spacing: Theme.Space.medium) {
                if entries.isEmpty {
                    Text(L10n.commonNotStated)
                        .font(.subheadline)
                        .foregroundStyle(Theme.Palette.textSecondary)
                } else {
                    ForEach(entries) { entry in
                        VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
                            Text(entry.sourceName)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Theme.Palette.textPrimary)
                                .fixedSize(horizontal: false, vertical: true)

                            LabelledValue(
                                L10n.detailRetrievedAtLabel,
                                value: entry.retrievedAt.map(Formatters.timestamp) ?? L10n.commonNotStated
                            )
                            LabelledValue(
                                L10n.detailLicenceLabel,
                                value: entry.licence ?? L10n.commonNotStated
                            )

                            BadgeFlow {
                                RightsBadge(entry.rightsStatus)
                                if let url = entry.sourceUrl {
                                    StatusBadge(url.host() ?? url.absoluteString, systemImage: "link", tone: .neutral)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityElement(children: .combine)

                        if entry.id != entries.last?.id { Divider() }
                    }
                }

                if let correctionUrl {
                    Button {
                        openingCorrection = correctionUrl
                    } label: {
                        Label(L10n.detailCorrection, systemImage: "exclamationmark.bubble")
                    }
                    .buttonStyle(OdivreloSecondaryButtonStyle())
                    .accessibilityIdentifier("provenance.correction")
                }
            }
        }
        .sheet(
            item: Binding(
                get: { openingCorrection.map(IdentifiedURL.init) },
                set: { openingCorrection = $0?.url }
            )
        ) { value in
            SafariSheet(url: value.url)
        }
    }
}

struct RightsBadge: View {
    private let status: RightsStatus

    init(_ status: RightsStatus) {
        self.status = status
    }

    var body: some View {
        switch status {
        case .allowed:
            StatusBadge(L10n.rightsAllowed, systemImage: "checkmark.seal", tone: .success)
        case .permissionPending:
            StatusBadge(L10n.rightsPending, systemImage: "hourglass", tone: .warning)
        case .prohibited:
            StatusBadge(L10n.rightsProhibited, systemImage: "xmark.seal", tone: .error)
        case .unknown:
            StatusBadge(L10n.rightsUnknown, systemImage: "questionmark.circle", tone: .neutral)
        }
    }
}
