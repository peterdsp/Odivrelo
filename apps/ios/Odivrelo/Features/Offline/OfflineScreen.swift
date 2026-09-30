import SwiftUI

/// Offline packs: sizes, progress with its real phase, retry, cancel,
/// integrity verification, atomic install, interrupted-download recovery,
/// update, rollback and delete.
///
/// Every one of those is the core's work. This screen starts it, shows the
/// phase the core reports, and states plainly which parts of the product do and
/// do not work without a connection, using the core's own availability record
/// rather than a guess.
struct OfflineScreen: View {
    @Environment(AppModel.self) private var model
    @State private var downloads = PackDownloadCoordinator()
    @State private var state: LoadState = .loading
    @State private var confirmingRollback = false

    let geometry: WindowGeometry

    private enum LoadState: Equatable {
        case loading
        case loaded(OfflineCatalog)
        case failed(CoreError)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.medium) {
                if model.isDemonstrationData { DemoDataNotice() }

                switch state {
                case .loading:
                    StateMessageView(kind: .loading, title: L10n.commonLoading)
                case let .failed(error):
                    CoreErrorView(error) { Task { await load() } }
                case let .loaded(catalog):
                    content(catalog)
                }
            }
            .readableColumn(geometry)
            .padding(.vertical, Theme.Space.medium)
        }
        .background(Theme.Palette.background)
        .navigationTitle(L10n.tabOffline)
        .refreshable { await load() }
        .task {
            downloads.attach(model)
            await load()
        }
        .confirmationDialog(
            L10n.offlineRollback,
            isPresented: $confirmingRollback,
            titleVisibility: .visible
        ) {
            Button(L10n.offlineRollback, role: .destructive) {
                Task { await rollback() }
            }
            Button(L10n.commonCancel, role: .cancel) {}
        }
    }

    @ViewBuilder
    private func content(_ catalog: OfflineCatalog) -> some View {
        if !catalog.manifestReachable {
            InlineNotice(
                text: L10n.offlineManifestUnreachable,
                systemImage: "antenna.radiowaves.left.and.right.slash",
                tone: .warning
            )
        }

        availability(catalog.mapAvailability)
        release(catalog)
        packs(catalog)
        installed(catalog)
    }

    /// The core's own statement of what works offline, repeated verbatim.
    private func availability(_ availability: OfflineMapAvailability) -> some View {
        SectionCard(L10n.offlineMapsNote, systemImage: "map") {
            VStack(alignment: .leading, spacing: Theme.Space.xSmall) {
                capability(
                    availability.searchAvailable
                        ? L10n.offlineSearchAvailable : L10n.offlineSearchUnavailable,
                    isAvailable: availability.searchAvailable
                )
                capability(
                    L10n.offlineStopCoordinatesAvailable,
                    isAvailable: availability.stopCoordinatesAvailable
                )
                capability(
                    L10n.offlineRouteGeometryAvailable,
                    isAvailable: availability.routeGeometryAvailable
                )
                capability(
                    L10n.offlineBaseMapUnavailable,
                    isAvailable: availability.baseMapTilesAvailable
                )
            }
        }
    }

    private func capability(_ text: String, isAvailable: Bool) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Theme.Space.xSmall) {
            Image(systemName: isAvailable ? "checkmark.circle.fill" : "xmark.circle")
                .foregroundStyle(isAvailable ? Theme.Palette.successText : Theme.Palette.textSecondary)
                .accessibilityHidden(true)
            Text(text)
                .font(.footnote)
                .foregroundStyle(Theme.Palette.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }

    private func release(_ catalog: OfflineCatalog) -> some View {
        SectionCard(L10n.offlineCurrentRelease, systemImage: "shippingbox") {
            VStack(alignment: .leading, spacing: Theme.Space.small) {
                LabelledValue(
                    L10n.offlineReleaseLabel,
                    value: String(catalog.releaseId.prefix(16)),
                    monospaced: true
                )
                LabelledValue(
                    L10n.offlineStorageUsedLabel,
                    value: catalog.installed.isEmpty
                        ? L10n.offlineNoneInstalled
                        : Formatters.bytes(catalog.totalInstalledBytes)
                )
                if let publishedAt = catalog.publishedAt {
                    LabelledValue(L10n.offlinePublishedLabel, value: Formatters.timestamp(publishedAt))
                }

                if let previous = catalog.rollbackReleaseId {
                    LabelledValue(
                        L10n.offlineRollbackTargetLabel,
                        value: String(previous.prefix(16)),
                        monospaced: true
                    )
                    Button(L10n.offlineRollback, role: .destructive) {
                        confirmingRollback = true
                    }
                    .buttonStyle(OdivreloSecondaryButtonStyle())
                    .accessibilityIdentifier("offline.rollback")
                } else {
                    Text(L10n.offlineNoPrevious)
                        .font(.footnote)
                        .foregroundStyle(Theme.Palette.textSecondary)
                }
            }
        }
    }

    private func packs(_ catalog: OfflineCatalog) -> some View {
        SectionCard(L10n.offlineAvailable, systemImage: "arrow.down.circle") {
            VStack(alignment: .leading, spacing: Theme.Space.medium) {
                ForEach(catalog.available) { pack in
                    packRow(pack)
                    if pack.id != catalog.available.last?.id { Divider() }
                }
            }
        }
    }

    private func packRow(_ pack: AvailablePack) -> some View {
        VStack(alignment: .leading, spacing: Theme.Space.xSmall) {
            Text(pack.title.resolved(for: language))
                .font(.body.weight(.semibold))
                .foregroundStyle(Theme.Palette.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            Text(pack.summary.resolved(for: language))
                .font(.footnote)
                .foregroundStyle(Theme.Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            BadgeFlow {
                StatusBadge(Formatters.bytes(pack.bytes), systemImage: "externaldrive", tone: .neutral)
                if pack.installed {
                    StatusBadge(L10n.offlineInstalled, systemImage: "checkmark.circle", tone: .success)
                }
                if pack.updateAvailable {
                    StatusBadge(L10n.offlineUpdateAvailable, systemImage: "arrow.clockwise", tone: .info)
                }
            }

            if let activity = downloads.activity[pack.name] {
                progress(activity, pack: pack)
            } else {
                HStack(spacing: Theme.Space.small) {
                    Button(pack.installed ? L10n.commonUpdate : L10n.offlineDownload) {
                        downloads.start(packName: pack.name) { Task { await load() } }
                    }
                    .buttonStyle(OdivreloSecondaryButtonStyle())
                    .accessibilityIdentifier("offline.download.\(pack.name)")

                    if pack.installed {
                        Button(L10n.commonDelete, role: .destructive) {
                            Task { await remove(pack.name) }
                        }
                        .buttonStyle(OdivreloSecondaryButtonStyle())
                    }
                    Spacer(minLength: 0)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func progress(_ activity: PackDownloadCoordinator.Activity, pack: AvailablePack) -> some View {
        switch activity {
        case let .running(value):
            VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
                if let fraction = value.fraction {
                    ProgressView(value: fraction) {
                        Text(phaseLabel(value.phase))
                    }
                    .accessibilityValue(L10n.a11yProgressPercent(Int(fraction * 100)))
                } else {
                    ProgressView { Text(phaseLabel(value.phase)) }
                }

                HStack(spacing: Theme.Space.xSmall) {
                    Text(
                        Formatters.bytes(value.bytesDownloaded) + " / "
                            + Formatters.bytes(value.totalBytes)
                    )
                    if value.attempt > 1 {
                        Text(L10n.offlineAttempt(value.attempt))
                            .foregroundStyle(Theme.Palette.warningText)
                    }
                }
                .font(.caption)
                .foregroundStyle(Theme.Palette.textSecondary)

                Button(L10n.commonCancel, role: .cancel) {
                    downloads.cancel(packName: pack.name)
                }
                .buttonStyle(OdivreloSecondaryButtonStyle())
                .accessibilityIdentifier("offline.cancel.\(pack.name)")
            }

        case let .failed(error):
            let presentation = ErrorPresentation.of(error)
            VStack(alignment: .leading, spacing: Theme.Space.xSmall) {
                InlineNotice(
                    text: presentation.title,
                    systemImage: presentation.systemImage,
                    tone: .error
                )
                // Retrying cannot help with some failures, a full disk being
                // the plain case, so the button is offered only when it can.
                if presentation.isRetryable {
                    Button(L10n.commonRetry) {
                        downloads.start(packName: pack.name) { Task { await load() } }
                    }
                    .buttonStyle(OdivreloSecondaryButtonStyle())
                    .accessibilityIdentifier("offline.retry.\(pack.name)")
                }
            }
        }
    }

    private func phaseLabel(_ phase: PackPhase) -> String {
        switch phase {
        case .queued: L10n.offlinePhaseQueued
        case .downloading: L10n.offlineDownload
        case .resuming: L10n.offlinePhaseResuming
        case .verifying: L10n.offlineVerifying
        case .installing: L10n.offlineInstalling
        case .done: L10n.offlinePhaseDone
        }
    }

    @ViewBuilder
    private func installed(_ catalog: OfflineCatalog) -> some View {
        if !catalog.installed.isEmpty {
            SectionCard(L10n.offlineInstalled, systemImage: "internaldrive") {
                VStack(alignment: .leading, spacing: Theme.Space.small) {
                    ForEach(catalog.installed) { pack in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(pack.name)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(Theme.Palette.textPrimary)
                            LabelledValue(
                                L10n.offlineInstalledAtLabel,
                                value: Formatters.timestamp(pack.installedAt)
                            )
                            LabelledValue(
                                L10n.offlineReleaseLabel,
                                value: String(pack.releaseId.prefix(16)),
                                monospaced: true
                            )
                            LabelledValue(
                                L10n.offlineStorageUsedLabel,
                                value: Formatters.bytes(pack.bytes)
                            )
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityElement(children: .combine)

                        if pack.id != catalog.installed.last?.id { Divider() }
                    }
                }
            }
        }
    }

    // MARK: Behaviour

    private var language: String { model.settings.effectiveLanguageTag }

    private func load() async {
        do {
            state = .loaded(try await model.core.offlineCatalog())
        } catch let error as CoreError {
            state = .failed(error)
        } catch {
            state = .failed(CoreErrorTranslation.translate(error))
        }
    }

    private func remove(_ packName: String) async {
        try? await model.core.removePack(packName: packName)
        await load()
    }

    private func rollback() async {
        do {
            _ = try await model.core.rollbackToPreviousRelease()
            await model.loadMeta(force: true)
            await load()
        } catch let error as CoreError {
            state = .failed(error)
        } catch {
            state = .failed(CoreErrorTranslation.translate(error))
        }
    }
}

/// Holds the in-flight downloads.
///
/// It lives above the view body, so a resize, a rotation, a posture change or a
/// trip to another tab cannot restart a download: the handle and its progress
/// survive every one of those.
@Observable
@MainActor
final class PackDownloadCoordinator {
    enum Activity: Equatable {
        case running(PackProgress)
        case failed(CoreError)
    }

    private(set) var activity: [String: Activity] = [:]
    private var handles: [String: any CoreCancellable] = [:]
    private weak var model: AppModel?

    func attach(_ model: AppModel) {
        self.model = model
    }

    func start(packName: String, onFinished: @escaping @Sendable @MainActor () -> Void) {
        guard let model, handles[packName] == nil else { return }

        activity[packName] = .running(
            PackProgress(
                packName: packName, bytesDownloaded: 0, totalBytes: 0,
                phase: .queued, attempt: 1
            )
        )

        handles[packName] = model.core.downloadPack(
            packName: packName,
            onProgress: { [weak self] progress in
                Task { @MainActor in
                    guard let self else { return }
                    self.activity[progress.packName] = .running(progress)
                }
            },
            onResult: { [weak self] result in
                Task { @MainActor in
                    guard let self else { return }
                    self.handles[result.packName] = nil
                    if result.succeeded {
                        self.activity[result.packName] = nil
                        onFinished()
                    } else {
                        let error = CoreErrorTranslation.fromPack(result)
                        if error == .cancelled {
                            self.activity[result.packName] = nil
                        } else {
                            self.activity[result.packName] = .failed(error)
                            AccessibilityAnnouncer.announce(
                                L10n.a11yErrorAnnouncement(ErrorPresentation.of(error).title)
                            )
                        }
                        onFinished()
                    }
                }
            }
        )
    }

    func cancel(packName: String) {
        handles[packName]?.cancel()
        handles[packName] = nil
        activity[packName] = nil
    }
}
