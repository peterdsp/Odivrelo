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
    /// Where the person can go to put this right, when somewhere exists.
    ///
    /// An error that names no route leaves someone stuck on a screen that only
    /// apologises, which is the state a new installation was in: it said the
    /// service could not answer and offered nowhere to go.
    public var recovery: Recovery?

    /// The one place in this application that can resolve a data failure.
    public enum Recovery: Hashable, Sendable {
        case offlinePacks

        public var label: String {
            switch self {
            case .offlinePacks: L10n.errorOpenOffline
            }
        }
    }

    public init(
        title: String,
        message: String,
        systemImage: String,
        isRetryable: Bool,
        hint: String? = nil,
        recovery: Recovery? = nil
    ) {
        self.title = title
        self.message = message
        self.systemImage = systemImage
        self.isRetryable = isRetryable
        self.hint = hint
        self.recovery = recovery
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
        case let .unavailable(kind):
            // The core says which kind of unavailability this is, and each one
            // has a different useful next step. Collapsing them into one
            // apology is what left a new installation with a screen that only
            // said the service could not answer.
            switch kind {
            case .noDataInstalled:
                ErrorPresentation(
                    title: L10n.errorNoDataInstalled,
                    message: L10n.errorNoDataInstalledHint,
                    systemImage: "arrow.down.circle",
                    isRetryable: false,
                    recovery: .offlinePacks
                )
            case .incompleteData:
                ErrorPresentation(
                    title: L10n.errorIncompleteData,
                    message: L10n.errorIncompleteDataHint,
                    systemImage: "exclamationmark.arrow.triangle.2.circlepath",
                    isRetryable: false,
                    recovery: .offlinePacks
                )
            case .unreadableData:
                ErrorPresentation(
                    title: L10n.errorUnreadableData,
                    message: L10n.errorUnreadableDataHint,
                    systemImage: "doc.badge.gearshape",
                    isRetryable: false,
                    recovery: .offlinePacks
                )
            case .noOfflinePackForDate:
                // This is a statement about what the device holds, never about
                // whether a service runs. The two are different answers and the
                // wording keeps them apart.
                ErrorPresentation(
                    title: L10n.errorNoOfflineDataForDate,
                    message: L10n.errorNoOfflineDataForDateHint,
                    systemImage: "calendar.badge.exclamationmark",
                    isRetryable: false,
                    recovery: .offlinePacks
                )
            case .storageFull:
                ErrorPresentation(
                    title: L10n.errorPackStorageFull,
                    message: L10n.offlineStorageUsedLabel,
                    systemImage: "externaldrive.badge.exclamationmark",
                    isRetryable: false,
                    recovery: .offlinePacks
                )
            case .networkUnavailable:
                ErrorPresentation(
                    title: L10n.errorOffline,
                    message: L10n.errorOfflineHint,
                    systemImage: "wifi.slash",
                    isRetryable: true,
                    hint: L10n.errorOfflineHint
                )
            case .general, .releaseMismatch, .notFound, .invalidRequest:
                ErrorPresentation(
                    title: L10n.errorUnavailable,
                    message: L10n.errorOfflineHint,
                    systemImage: "clock.badge.exclamationmark",
                    isRetryable: true
                )
            }
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
    @Environment(AppModel.self) private var model
    private let error: CoreError
    private let retry: (() -> Void)?

    public init(_ error: CoreError, retry: (() -> Void)? = nil) {
        self.error = error
        self.retry = retry
    }

    public var body: some View {
        let presentation = ErrorPresentation.of(error)
        VStack(spacing: Theme.Space.medium) {
            StateMessageView(
                kind: .failure(systemImage: presentation.systemImage),
                title: presentation.title,
                message: presentation.message.isEmpty ? nil : presentation.message,
                retry: presentation.isRetryable ? retry : nil
            )
            // A failure that can be put right says where. Without this the
            // first screen of a new installation only apologised.
            if let recovery = presentation.recovery {
                Button(recovery.label) { take(recovery) }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("error.recovery")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(L10n.a11yErrorAnnouncement(presentation.title))
        .onAppear {
            // VoiceOver is told about a failure rather than leaving the reader
            // to discover a silently changed screen.
            AccessibilityAnnouncer.announce(L10n.a11yErrorAnnouncement(presentation.title))
        }
    }

    private func take(_ recovery: ErrorPresentation.Recovery) {
        switch recovery {
        case .offlinePacks:
            model.session.selectedTab = .offline
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
