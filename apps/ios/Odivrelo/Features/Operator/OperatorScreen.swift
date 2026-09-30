import SwiftUI
import UIKit

/// The operator page: verified details, attribution, coverage and the
/// correction path. Operator artwork is never reproduced, and the lack of any
/// affiliation is stated rather than implied.
struct OperatorScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.windowGeometry) private var geometry

    let operatorId: String

    @State private var state: LoadState = .loading
    @State private var openingURL: URL?

    private enum LoadState: Equatable {
        case loading
        case loaded(OperatorDetail)
        case failed(CoreError)
    }

    var body: some View {
        Group {
            switch state {
            case .loading:
                StateMessageView(kind: .loading, title: L10n.commonLoading)
            case let .failed(error):
                CoreErrorView(error) { Task { await load() } }
            case let .loaded(detail):
                content(detail)
            }
        }
        .background(Theme.Palette.background)
        .navigationTitle(L10n.operatorTitle)
        .task(id: operatorId) { await load() }
        .sheet(item: Binding(get: { openingURL.map(IdentifiedURL.init) }, set: { openingURL = $0?.url })) { value in
            SafariSheet(url: value.url)
        }
    }

    private func content(_ value: OperatorDetail) -> some View {
        let detail = value.operatorBody
        return ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.medium) {
                if model.isDemonstrationData { DemoDataNotice() }

                SectionCard(detail.name.resolved(for: language), systemImage: "bus") {
                    VStack(alignment: .leading, spacing: Theme.Space.small) {
                        if let number = detail.federationNumber {
                            LabelledValue(L10n.operatorTitle, value: String(number))
                        }
                        if let verifiedAt = detail.verifiedAt {
                            LabelledValue(
                                L10n.detailVerifiedAtLabel,
                                value: Formatters.timestamp(verifiedAt)
                            )
                        } else {
                            LabelledValue(L10n.detailVerifiedAtLabel, value: L10n.purchaseNotVerified)
                        }

                        InlineNotice(
                            text: L10n.operatorNotAffiliated,
                            systemImage: "info.circle",
                            tone: .info
                        )
                    }
                }

                SectionCard(L10n.operatorCoverage, systemImage: "map") {
                    VStack(alignment: .leading, spacing: Theme.Space.small) {
                        BadgeFlow { CoverageBadge(detail.coverage.state) }
                        LabelledValue(
                            L10n.operatorCoverage,
                            value: L10n.operatorRouteCount(detail.coverage.routeCount)
                                + " · " + L10n.operatorStopCount(detail.coverage.stopCount)
                        )
                    }
                }

                if let contact = detail.contact {
                    SectionCard(L10n.purchaseTicketOffice, systemImage: "phone") {
                        VStack(alignment: .leading, spacing: Theme.Space.small) {
                            if let address = contact.address, !address.isEmpty {
                                LabelledValue(L10n.purchaseAddress, value: address)
                                HStack(spacing: Theme.Space.small) {
                                    Button {
                                        UIPasteboard.general.string = address
                                    } label: {
                                        Label(L10n.commonCopy, systemImage: "doc.on.doc")
                                    }
                                    .buttonStyle(OdivreloSecondaryButtonStyle())
                                    if let url = PurchaseSection.directionsURL(for: address) {
                                        Button {
                                            UIApplication.shared.open(url)
                                        } label: {
                                            Label(L10n.commonDirections, systemImage: "location")
                                        }
                                        .buttonStyle(OdivreloSecondaryButtonStyle())
                                    }
                                }
                            }
                            if let phone = contact.phone, let url = PurchaseSection.telephoneURL(for: phone) {
                                LabelledValue(L10n.purchasePhone, value: phone)
                                Button {
                                    UIApplication.shared.open(url)
                                } label: {
                                    Label(L10n.commonCall, systemImage: "phone")
                                }
                                .buttonStyle(OdivreloSecondaryButtonStyle())
                            }
                            if let email = contact.email,
                               let url = URL(string: "mailto:\(email)") {
                                LabelledValue(L10n.commonEmail, value: email)
                                Button {
                                    UIApplication.shared.open(url)
                                } label: {
                                    Label(L10n.commonEmail, systemImage: "envelope")
                                }
                                .buttonStyle(OdivreloSecondaryButtonStyle())
                            }
                            if let site = detail.officialSiteUrl {
                                Button {
                                    openingURL = site
                                } label: {
                                    Label(L10n.operatorOfficial, systemImage: "arrow.up.forward.square")
                                }
                                .buttonStyle(OdivreloSecondaryButtonStyle())
                                .accessibilityIdentifier("operator.official")
                            }
                        }
                    }
                }

                ProvenanceSection(entries: detail.sources, correctionUrl: detail.correctionUrl)
            }
            .readableColumn(geometry)
            .padding(.vertical, Theme.Space.medium)
        }
    }

    private var language: String { model.settings.effectiveLanguageTag }

    private func load() async {
        state = .loading
        do {
            state = .loaded(try await model.core.operatorDetail(operatorId: operatorId))
        } catch let error as CoreError {
            state = .failed(error)
        } catch {
            state = .failed(CoreErrorTranslation.translate(error))
        }
    }
}
