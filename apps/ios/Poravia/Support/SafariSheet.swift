import SafariServices
import SwiftUI
import UIKit

/// Opens an operator's own page in `SFSafariViewController`.
///
/// Presenting it as a sheet over the current screen is what preserves state:
/// the navigation stack, the query, the service date, the filters, the
/// selection and the scroll position are all still there when the sheet is
/// dismissed, because nothing below it was torn down.
///
/// Nothing about the traveller is added to the URL. The controller is
/// configured not to offer to hand the page on to another app or to a reading
/// list, so the handoff stays the operator's page and nothing else.
struct SafariSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let configuration = SFSafariViewController.Configuration()
        configuration.entersReaderIfAvailable = false
        configuration.barCollapsingEnabled = true

        let controller = SFSafariViewController(url: url, configuration: configuration)
        controller.preferredControlTintColor = UIColor(Theme.Palette.primary)
        controller.preferredBarTintColor = UIColor(Theme.Palette.surface)
        controller.dismissButtonStyle = .done
        return controller
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

/// Opens the system Settings page for this app, used where a permission was
/// refused and the only way forward is in Settings.
enum SystemSettings {
    @MainActor
    static func open() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
