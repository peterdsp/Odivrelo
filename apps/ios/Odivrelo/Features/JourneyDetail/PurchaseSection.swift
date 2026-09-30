import SwiftUI
import UIKit

/// The official booking handoff.
///
/// Odivrelo never sells or issues a ticket. When the operator has a verified
/// online store the primary action names the operator and opens the operator's
/// own page; when it does not, the action becomes a complete, verified contact
/// and ticket-office card instead, and the copy says the sales channel has not
/// been investigated rather than that it does not exist.
struct PurchaseSection: View {
    let purchase: PurchaseOption
    let operatorName: String
    let onOpen: (URL) -> Void

    var body: some View {
        SectionCard(L10n.purchaseHeading, systemImage: "ticket") {
            VStack(alignment: .leading, spacing: Theme.Space.small) {
                switch purchase.kind {
                case .online:
                    online
                case .ticketOffice:
                    office
                case .phone:
                    phone
                case .onboard:
                    onboard
                case .unavailable:
                    unknown
                }

                if let disclaimer = purchase.disclaimer {
                    Text(disclaimer.resolved(for: AppSettings.shared.effectiveLanguageTag))
                        .font(.footnote)
                        .foregroundStyle(Theme.Palette.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Text(L10n.purchaseOperatorHandles)
                    .font(.caption)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                verification
            }
        }
    }

    // MARK: Variants

    @ViewBuilder
    private var online: some View {
        if let url = purchase.url {
            Button {
                onOpen(url)
            } label: {
                Label(L10n.purchaseContinueToOperator(operatorName), systemImage: "arrow.up.forward.square")
            }
            .buttonStyle(OdivreloPrimaryButtonStyle())
            .accessibilityIdentifier("purchase.online")

            LabelledValue(L10n.operatorOfficial, value: url.host() ?? url.absoluteString)

            Text(L10n.purchaseOpensInBrowser)
                .font(.caption)
                .foregroundStyle(Theme.Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        } else {
            unknown
        }
    }

    private var office: some View {
        VStack(alignment: .leading, spacing: Theme.Space.small) {
            Text(L10n.purchaseHowToBuy)
                .font(.body.weight(.semibold))
                .foregroundStyle(Theme.Palette.textPrimary)

            contactRows
        }
    }

    private var phone: some View {
        VStack(alignment: .leading, spacing: Theme.Space.small) {
            Text(L10n.purchaseHowToBuy)
                .font(.body.weight(.semibold))
                .foregroundStyle(Theme.Palette.textPrimary)
            contactRows
        }
    }

    private var onboard: some View {
        InlineNotice(text: L10n.purchaseOnboard, systemImage: "bus", tone: .info)
    }

    private var unknown: some View {
        // Deliberately not "electronic ticketing unavailable": nobody has
        // checked, and saying otherwise would be a claim the data does not
        // support.
        InlineNotice(
            text: L10n.purchaseUnknownElectronic,
            systemImage: "questionmark.circle",
            tone: .warning
        )
    }

    @ViewBuilder
    private var contactRows: some View {
        if let address = purchase.address, !address.isEmpty {
            LabelledValue(L10n.purchaseAddress, value: address)
            HStack(spacing: Theme.Space.small) {
                Button {
                    UIPasteboard.general.string = address
                } label: {
                    Label(L10n.commonCopy, systemImage: "doc.on.doc")
                }
                .buttonStyle(OdivreloSecondaryButtonStyle())

                if let url = Self.directionsURL(for: address) {
                    Button {
                        UIApplication.shared.open(url)
                    } label: {
                        Label(L10n.commonDirections, systemImage: "location")
                    }
                    .buttonStyle(OdivreloSecondaryButtonStyle())
                }
            }
        }

        if let phone = purchase.phone, !phone.isEmpty {
            LabelledValue(L10n.purchasePhone, value: phone)
            if let url = Self.telephoneURL(for: phone) {
                Button {
                    UIApplication.shared.open(url)
                } label: {
                    Label(L10n.commonCall, systemImage: "phone")
                }
                .buttonStyle(OdivreloSecondaryButtonStyle())
                .accessibilityIdentifier("purchase.call")
            }
        }

        if let hours = purchase.openingHours, !hours.isEmpty {
            LabelledValue(L10n.purchaseOpeningHours, value: hours)
        }
    }

    /// Whether the sales channel is usable with no network, which is the fact
    /// that matters at a terminal with no signal.
    private var verification: some View {
        BadgeFlow {
            if purchase.hasOfflineFallback {
                StatusBadge(L10n.purchaseTicketOffice, systemImage: "phone", tone: .success)
            }
            if purchase.isExternalHandoff {
                StatusBadge(L10n.operatorOfficial, systemImage: "arrow.up.forward.square", tone: .info)
            }
        }
    }

    // MARK: URLs

    /// A maps URL built from an address. The address is percent-encoded, and
    /// nothing about the traveller or a ticket is ever placed in a URL.
    static func directionsURL(for address: String) -> URL? {
        guard let encoded = address.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return nil
        }
        return URL(string: "http://maps.apple.com/?address=\(encoded)")
    }

    static func telephoneURL(for phone: String) -> URL? {
        let digits = phone.filter { $0.isNumber || $0 == "+" }
        guard digits.count >= 5 else { return nil }
        return URL(string: "tel://\(digits)")
    }
}
