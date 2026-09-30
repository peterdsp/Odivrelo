package dev.peterdsp.poravia.ui.wallet

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.peterdsp.poravia.R
import dev.peterdsp.poravia.state.AppState
import dev.peterdsp.poravia.state.PoraviaViewModel
import dev.peterdsp.poravia.state.WalletMessage
import dev.peterdsp.poravia.theme.Space
import dev.peterdsp.poravia.ui.common.Badge
import dev.peterdsp.poravia.ui.common.Formats
import dev.peterdsp.poravia.ui.common.InfoPanel
import dev.peterdsp.poravia.ui.common.OutcomeState
import dev.peterdsp.poravia.ui.common.PoraviaButton
import dev.peterdsp.poravia.ui.common.PoraviaCard
import dev.peterdsp.poravia.ui.common.PoraviaScreen
import dev.peterdsp.poravia.ui.common.Tone
import dev.peterdsp.poravia.ui.currentLocale
import dev.peterdsp.poravia.wallet.TicketKind
import dev.peterdsp.poravia.wallet.TicketRecord
import dev.peterdsp.poravia.wallet.TicketStore

/**
 * The travel wallet.
 *
 * Tickets get in one way only: the person picks a single document through the
 * Storage Access Framework. There is no storage permission of any kind, the
 * application never scans anything, and a file is refused unless its own
 * leading bytes say it is a PDF, a PNG or a JPEG.
 *
 * Nothing here is uploaded, logged, put in a notification, put in a URL or put
 * in diagnostics. Deleting is real, and the screen says so before asking.
 */
@Composable
fun WalletScreen(state: AppState, viewModel: PoraviaViewModel) {
    val context = LocalContext.current
    val locale = currentLocale()
    var confirmingDelete by rememberSaveable { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importTicket(uri) }

    PoraviaScreen(title = stringResource(R.string.wallet_title)) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("wallet-list"),
            contentPadding = PaddingValues(Space.x4),
            verticalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            item(key = "intro") {
                Column {
                    Text(
                        text = stringResource(R.string.wallet_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        text = stringResource(R.string.wallet_never_uploaded),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        text = stringResource(R.string.wallet_stored_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "import") {
                PoraviaButton(
                    text = stringResource(R.string.wallet_import),
                    onClick = {
                        picker.launch(TicketKind.entries.map { it.mediaType }.toTypedArray())
                    },
                    modifier = Modifier.fillMaxWidth().testTag("wallet-import"),
                )
            }

            state.walletMessage?.let { message ->
                item(key = "message") {
                    InfoPanel(
                        title = when (message) {
                            WalletMessage.UnsupportedType ->
                                context.getString(R.string.wallet_invalid_type)

                            is WalletMessage.TooLarge -> context.getString(
                                R.string.wallet_too_large,
                                Formats.bytes(message.limitBytes),
                            )

                            WalletMessage.StorageFull ->
                                context.getString(R.string.state_storage_full_title)

                            WalletMessage.Unreadable ->
                                context.getString(R.string.wallet_import_failed)

                            WalletMessage.Deleted ->
                                context.getString(R.string.wallet_delete_confirm)
                        },
                        body = null,
                        tone = if (message == WalletMessage.Deleted) Tone.NEUTRAL else Tone.ERROR,
                        modifier = Modifier.testTag("wallet-message"),
                    ) {
                        PoraviaButton(
                            text = stringResource(R.string.action_close),
                            onClick = viewModel::clearWalletMessage,
                            outlined = true,
                        )
                    }
                }
            }

            if (state.tickets.isEmpty()) {
                item(key = "empty") {
                    OutcomeState(
                        title = context.getString(R.string.wallet_empty_title),
                        body = context.getString(R.string.wallet_empty_body),
                        isError = false,
                        modifier = Modifier.testTag("wallet-empty"),
                    )
                }
            } else {
                items(state.tickets, key = { it.id }) { ticket ->
                    TicketCard(
                        ticket = ticket,
                        locale = locale,
                        isOpen = state.session.openTicketId == ticket.id,
                        bytes = if (state.session.openTicketId == ticket.id) {
                            state.ticketBytes
                        } else {
                            null
                        },
                        onOpen = { viewModel.openTicket(ticket.id) },
                        onHide = { viewModel.closeTicket() },
                        onDelete = { confirmingDelete = ticket.id },
                    )
                }
            }
        }
    }

    confirmingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text(stringResource(R.string.wallet_delete)) },
            text = { Text(stringResource(R.string.wallet_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTicket(id)
                        confirmingDelete = null
                    },
                    modifier = Modifier.testTag("wallet-delete-confirm"),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun TicketCard(
    ticket: TicketRecord,
    locale: java.util.Locale,
    isOpen: Boolean,
    bytes: ByteArray?,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    PoraviaCard(modifier = Modifier.testTag("ticket-" + ticket.id)) {
        Text(ticket.label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            Badge(ticket.mediaType, Tone.NEUTRAL)
            Badge(context.getString(R.string.wallet_file_size, Formats.bytes(ticket.bytes)), Tone.NEUTRAL)
        }
        Spacer(Modifier.height(Space.x2))
        Text(
            text = context.getString(
                R.string.wallet_imported_at,
                Formats.timestamp(ticket.importedAt, locale).orEmpty(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Space.x3))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            PoraviaButton(
                text = stringResource(
                    if (isOpen) R.string.wallet_hide_ticket else R.string.wallet_open_ticket,
                ),
                onClick = if (isOpen) onHide else onOpen,
                modifier = Modifier.weight(1f).testTag("ticket-open-" + ticket.id),
            )
            PoraviaButton(
                text = stringResource(R.string.wallet_delete),
                onClick = onDelete,
                outlined = true,
                modifier = Modifier.weight(1f).testTag("ticket-delete-" + ticket.id),
            )
        }

        if (isOpen) {
            Spacer(Modifier.height(Space.x3))
            Text(
                text = stringResource(R.string.wallet_barcode_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.x1))
            Text(
                text = stringResource(R.string.wallet_locked_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.x3))
            TicketPreview(ticket, bytes)
        }
    }
}

/**
 * Draws a ticket without ever letting its plaintext settle anywhere.
 *
 * An image is decoded straight from the decrypted bytes in memory. A PDF cannot
 * be: `PdfRenderer` needs a seekable descriptor, so the store writes the
 * plaintext into the application's own cache under a random name and unlinks it
 * before returning, leaving bytes that only the open descriptor can reach.
 *
 * `FLAG_SECURE` is set for as long as a ticket is on screen, so the recents
 * thumbnail and a screen recording both see nothing.
 */
@Composable
private fun TicketPreview(ticket: TicketRecord, bytes: ByteArray?) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(ticket.id) {
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    if (bytes == null) {
        dev.peterdsp.poravia.ui.common.LoadingState()
        return
    }

    var bitmap by remember(ticket.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(ticket.id) { mutableStateOf(false) }

    LaunchedEffect(ticket.id, bytes.size) {
        bitmap = when (ticket.kind) {
            TicketKind.PNG, TicketKind.JPEG ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

            TicketKind.PDF -> renderFirstPage(context, ticket.id)
            null -> null
        }
        failed = bitmap == null
    }

    val drawn = bitmap
    if (drawn != null) {
        Image(
            bitmap = drawn.asImageBitmap(),
            contentDescription = ticket.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(drawn.width.toFloat() / drawn.height.toFloat().coerceAtLeast(1f))
                .testTag("ticket-preview")
                .semantics { contentDescription = ticket.label },
        )
    } else if (failed) {
        InfoPanel(
            title = stringResource(R.string.wallet_import_failed),
            body = null,
            tone = Tone.ERROR,
            modifier = Modifier.testTag("ticket-preview-failed"),
        )
    } else {
        dev.peterdsp.poravia.ui.common.LoadingState()
    }
}

private suspend fun renderFirstPage(
    context: android.content.Context,
    ticketId: String,
): Bitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val store = TicketStore(context)
    val descriptor = store.openForRendering(ticketId) ?: return@withContext null
    descriptor.use { fd ->
        runCatching {
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) return@runCatching null
                renderer.openPage(0).use { page ->
                    val scale = (PDF_TARGET_WIDTH.toFloat() / page.width).coerceAtMost(MAX_SCALE)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    // A PDF page is transparent where it has no ink, which would
                    // render a black rectangle on a dark theme.
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }.getOrNull()
    }
}

private const val PDF_TARGET_WIDTH = 1400
private const val MAX_SCALE = 4f
