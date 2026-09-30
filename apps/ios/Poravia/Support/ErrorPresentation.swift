import SwiftUI

/// Turns a `CoreError` into the exact words shown for it.
///
/// Every case gets its own message. There is no catch-all "something went
/// wrong": a person who cannot search needs to know whether the network is
/// down, the data release disagrees, the disk is full or this build simply has
/// no data core, because the useful next step differs in each case.
public struct ErrorPresentation: Equatable, Sendable {
    public var title: String
    public var message: String
    public var systemImage: String
    /// Whether retrying the same request could plausibly succeed.
    public var isRetryable: Bool
    /// A secondary hint, such as what still works offline.
    public var hint: String?

    public init(
        title: String,
        message: String,
        systemImage: String,
        isRetryable: Bool,
        hint: String? = nil
    ) {
        self.title = title
        self.message = message
        self.systemImage = systemImage
        self.isRetryable = isRetryable
        self.hint = hint
    }

    public static func of(_ error: CoreError) -> ErrorPresentation {
        switch error {
        case .offline:
            ErrorPresentation(
                title: L10n.errorOffline,
                message: L10n.errorOfflineHint,
                systemImage: "wifi.slash",
                isRetryable: true,
                hint: L10n.errorOfflineHint
            )
        case .unavailable:
            ErrorPresentation(
                title: L10n.errorUnavailable,
                message: L10n.errorOfflineHint,
                systemImage: "clock.badge.exclamationmark",
                isRetryable: true
            )
        case .notFound:
            ErrorPresentation(
                title: L10n.errorNotFound,
                message: L10n.deeplinkSearchInstead,
                systemImage: "questionmark.circle",
                isRetryable: false
            )
        case let .invalidRequest(field):
            ErrorPresentation(
                title: L10n.errorInvalidRequest(field ?? L10n.commonNotStated),
                message: L10n.searchNeedBothPlaces,
                systemImage: "exclamationmark.triangle",
                isRetryable: false
            )
        case .releaseMismatch:
            ErrorPresentation(
                title: L10n.errorReleaseMismatch,
                message: L10n.offlineMapsNote,
                systemImage: "arrow.triangle.branch",
                isRetryable: true
            )
        case .unauthorized:
            ErrorPresentation(
                title: L10n.errorUnauthorized,
                message: L10n.errorUnavailable,
                systemImage: "lock.trianglebadge.exclamationmark",
                isRetryable: false
            )
        case let .pack(failure, _, message):
            packPresentation(failure, message: message)
        case .cancelled:
            ErrorPresentation(
                title: L10n.errorCancelled,
                message: "",
                systemImage: "xmark.circle",
                isRetryable: true
            )
        case let .coreUnavailable(reason):
            ErrorPresentation(
                title: L10n.errorCoreUnavailable,
                message: reason,
                systemImage: "shippingbox.and.arrow.backward",
                isRetryable: false
            )
        case let .unexpected(detail):
            ErrorPresentation(
                title: L10n.errorDecoding,
                message: detail,
                systemImage: "doc.badge.gearshape",
                isRetryable: true
            )
        }
    }

    /// Each pack failure gets its own words and its own next step: a corrupt
    /// download is worth retrying, a full disk is not.
    private static func packPresentation(_ failure: PackFailure, message: String?) -> ErrorPresentation {
        switch failure {
        case .network, .timeout:
            ErrorPresentation(
                title: L10n.errorOffline,
                message: L10n.errorInterrupted,
                systemImage: "wifi.slash",
                isRetryable: true
            )
        case .digestMismatch:
            ErrorPresentation(
                title: L10n.errorIntegrity,
                message: message ?? L10n.offlineMapsNote,
                systemImage: "shield.slash",
                isRetryable: true
            )
        case .releaseMismatch:
            ErrorPresentation(
                title: L10n.errorReleaseMismatch,
                message: L10n.offlineMapsNote,
                systemImage: "arrow.triangle.branch",
                isRetryable: true
            )
        case .storageFull:
            ErrorPresentation(
                title: L10n.errorPackStorageFull,
                message: message ?? "",
                systemImage: "externaldrive.badge.exclamationmark",
                isRetryable: false
            )
        case .notInManifest:
            ErrorPresentation(
                title: L10n.errorPackNotInManifest,
                message: L10n.offlineManifestUnreachable,
                systemImage: "questionmark.folder",
                isRetryable: false
            )
        case .cancelled:
            ErrorPresentation(
                title: L10n.errorCancelled,
                message: "",
                systemImage: "xmark.circle",
                isRetryable: true
            )
        case .io:
            ErrorPresentation(
                title: L10n.errorPackIo,
                message: message ?? "",
                systemImage: "externaldrive.badge.xmark",
                isRetryable: true
            )
        }
    }
}

/// Presents a `CoreError` with the shared state view and, when the error is
/// worth retrying, a retry action.
public struct CoreErrorView: View {
    private let error: CoreError
    private let retry: (() -> Void)?

    public init(_ error: CoreError, retry: (() -> Void)? = nil) {
        self.error = error
        self.retry = retry
    }

    public var body: some View {
        let presentation = ErrorPresentation.of(error)
        StateMessageView(
            kind: .failure(systemImage: presentation.systemImage),
            title: presentation.title,
            message: presentation.message.isEmpty ? nil : presentation.message,
            retry: presentation.isRetryable ? retry : nil
        )
        .accessibilityElement(children: .contain)
        .accessibilityLabel(L10n.a11yErrorAnnouncement(presentation.title))
        .onAppear {
            // VoiceOver is told about a failure rather than leaving the reader
            // to discover a silently changed screen.
            AccessibilityAnnouncer.announce(L10n.a11yErrorAnnouncement(presentation.title))
        }
    }
}

/// Posts a VoiceOver announcement when something changed that a sighted reader
/// would have noticed without moving focus.
public enum AccessibilityAnnouncer {
    @MainActor
    public static func announce(_ message: String) {
        guard UIAccessibility.isVoiceOverRunning else { return }
        var announcement = AttributedString(message)
        announcement.accessibilitySpeechAnnouncementPriority = .high
        AccessibilityNotification.Announcement(announcement).post()
    }
}
