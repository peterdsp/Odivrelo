import PDFKit
import SwiftUI
import UniformTypeIdentifiers

/// The travel wallet.
///
/// Only a file the person picked themselves ever enters it. Nothing here
/// uploads, nothing here logs a ticket's contents, nothing here puts ticket
/// data in a URL, and there is no analytics anywhere in the product to put it
/// in. `TicketStore` enforces the type check, the size limit, the file
/// protection and the backup exclusion.
struct WalletScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.windowGeometry) private var geometry

    @State private var isImporting = false
    @State private var viewing: StoredTicket?
    @State private var deleting: StoredTicket?
    @State private var importError: TicketImportError?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Theme.Space.medium) {
                explainer
                if model.wallet.tickets.isEmpty {
                    StateMessageView(
                        kind: .empty(systemImage: "wallet.pass"),
                        title: L10n.walletEmpty,
                        message: L10n.walletImportExplainer
                    )
                } else {
                    tickets
                }
            }
            .readableColumn(geometry)
            .padding(.vertical, Theme.Space.medium)
        }
        .background(Theme.Palette.background)
        .navigationTitle(L10n.walletTitle)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    isImporting = true
                } label: {
                    Label(L10n.walletImport, systemImage: "plus")
                }
                .accessibilityIdentifier("wallet.import")
            }
        }
        .fileImporter(
            isPresented: $isImporting,
            allowedContentTypes: TicketStore.acceptedTypes,
            allowsMultipleSelection: false
        ) { result in
            handleImport(result)
        }
        .sheet(item: $viewing) { ticket in
            TicketViewer(ticket: ticket)
        }
        .alert(
            item: Binding(
                get: { importError.map(IdentifiedImportError.init) },
                set: { importError = $0?.value }
            )
        ) { wrapped in
            Alert(
                title: Text(message(for: wrapped.value)),
                dismissButton: .default(Text(L10n.commonClose))
            )
        }
        .confirmationDialog(
            L10n.walletDeleteConfirm,
            isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
            titleVisibility: .visible
        ) {
            Button(L10n.commonDelete, role: .destructive) {
                if let deleting {
                    model.wallet.delete(deleting)
                    // The viewer is closed too: a deleted ticket must not stay
                    // on screen or come back through state restoration.
                    if viewing?.id == deleting.id { viewing = nil }
                    if model.session.viewingTicketId == deleting.id {
                        model.session.viewingTicketId = nil
                        model.persistSession()
                    }
                }
                deleting = nil
            }
            Button(L10n.commonCancel, role: .cancel) { deleting = nil }
        }
        .onAppear(perform: restoreViewingState)
        .onChange(of: viewing?.id) { _, newValue in
            // Only the identifier is remembered. The document itself is never
            // persisted outside its protected container file.
            model.session.viewingTicketId = newValue
            model.persistSession()
        }
    }

    private var explainer: some View {
        SectionCard(L10n.walletTitle, systemImage: "lock.doc") {
            VStack(alignment: .leading, spacing: Theme.Space.small) {
                Text(L10n.walletImportExplainer)
                    .font(.footnote)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(L10n.walletStorageExplainer)
                    .font(.footnote)
                    .foregroundStyle(Theme.Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                BadgeFlow {
                    StatusBadge(
                        model.wallet.isExcludedFromBackup
                            ? L10n.walletBackupExcluded : L10n.walletBackupNotExcluded,
                        systemImage: model.wallet.isExcludedFromBackup ? "lock.shield" : "exclamationmark.shield",
                        tone: model.wallet.isExcludedFromBackup ? .success : .warning
                    )
                    StatusBadge(
                        L10n.offlineStorageUsed(Formatters.bytes(model.wallet.totalBytes)),
                        systemImage: "externaldrive",
                        tone: .neutral
                    )
                }

                Button(L10n.walletImport) { isImporting = true }
                    .buttonStyle(PoraviaPrimaryButtonStyle())
            }
        }
    }

    private var tickets: some View {
        SectionCard(L10n.walletTitle, systemImage: "wallet.pass") {
            VStack(alignment: .leading, spacing: Theme.Space.small) {
                ForEach(model.wallet.tickets) { ticket in
                    VStack(alignment: .leading, spacing: Theme.Space.xxSmall) {
                        Text(ticket.displayName)
                            .font(.body.weight(.medium))
                            .foregroundStyle(Theme.Palette.textPrimary)
                            .fixedSize(horizontal: false, vertical: true)
                        BadgeFlow {
                            StatusBadge(
                                ticket.kind == .pdf ? "PDF" : "Image",
                                systemImage: ticket.kind == .pdf ? "doc.richtext" : "photo",
                                tone: .neutral
                            )
                            StatusBadge(
                                L10n.walletFileSize(Formatters.bytes(ticket.byteCount)),
                                systemImage: "externaldrive",
                                tone: .neutral
                            )
                            StatusBadge(
                                L10n.walletImportedAt(Formatters.timestamp(ticket.importedAt)),
                                systemImage: "calendar",
                                tone: .neutral
                            )
                        }
                        HStack(spacing: Theme.Space.small) {
                            Button(L10n.walletTitle) { viewing = ticket }
                                .buttonStyle(PoraviaSecondaryButtonStyle())
                                .accessibilityIdentifier("wallet.open.\(ticket.id)")
                            Button(L10n.commonDelete, role: .destructive) { deleting = ticket }
                                .buttonStyle(PoraviaSecondaryButtonStyle())
                                .accessibilityIdentifier("wallet.delete.\(ticket.id)")
                            Spacer(minLength: 0)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    if ticket.id != model.wallet.tickets.last?.id { Divider() }
                }
            }
        }
    }

    private func handleImport(_ result: Result<[URL], any Error>) {
        switch result {
        case let .success(urls):
            guard let url = urls.first else { return }
            if case let .failure(error) = model.wallet.importTicket(from: url) {
                importError = error
            }
        case .failure:
            importError = .accessDenied
        }
    }

    private func message(for error: TicketImportError) -> String {
        switch error {
        case .unsupportedType: L10n.walletUnsupportedType
        case let .tooLarge(limit): L10n.walletTooLarge(Formatters.bytes(limit))
        case .accessDenied: L10n.walletPickerDenied
        case .readFailed: L10n.walletReadFailed
        case .storageFull: L10n.errorPackStorageFull
        }
    }

    /// Restores which ticket was open, by identifier, re-reading the file from
    /// the protected container rather than from any saved copy.
    private func restoreViewingState() {
        guard let id = model.session.viewingTicketId else { return }
        viewing = model.wallet.ticket(withId: id)
        if viewing == nil {
            model.session.viewingTicketId = nil
            model.persistSession()
        }
    }
}

private struct IdentifiedImportError: Identifiable {
    let value: TicketImportError
    var id: String { String(describing: value) }
    init(_ value: TicketImportError) { self.value = value }
}

/// Renders a ticket.
///
/// PDFs go through PDFKit and images through `Image`. Neither path executes web
/// content, so a crafted file cannot run script inside the app.
struct TicketViewer: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    let ticket: StoredTicket

    var body: some View {
        NavigationStack {
            Group {
                switch ticket.kind {
                case .pdf:
                    PDFDocumentView(url: model.wallet.fileURL(for: ticket))
                case .image:
                    imageView
                }
            }
            .background(Theme.Palette.background)
            .navigationTitle(ticket.displayName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L10n.commonClose) { dismiss() }
                }
            }
        }
        // A ticket is not shown in the app switcher snapshot.
        .privacySensitive()
    }

    @ViewBuilder
    private var imageView: some View {
        if let image = UIImage(contentsOfFile: model.wallet.fileURL(for: ticket).path(percentEncoded: false)) {
            ScrollView([.horizontal, .vertical]) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .accessibilityLabel(ticket.displayName)
            }
        } else {
            StateMessageView(
                kind: .failure(systemImage: "doc.questionmark"),
                title: L10n.walletReadFailed
            )
        }
    }
}

/// PDFKit, wrapped. No web view is involved anywhere in ticket rendering.
struct PDFDocumentView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.backgroundColor = UIColor(Theme.Palette.background)
        view.document = PDFDocument(url: url)
        view.isAccessibilityElement = false
        return view
    }

    func updateUIView(_ view: PDFView, context: Context) {
        if view.document == nil { view.document = PDFDocument(url: url) }
    }
}
