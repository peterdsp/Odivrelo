package dev.peterdsp.poravia.wallet

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** What a ticket file is allowed to be. Nothing else is imported. */
enum class TicketKind(val mediaType: String) {
    PDF("application/pdf"),
    PNG("image/png"),
    JPEG("image/jpeg"),
    ;

    companion object {
        /**
         * Decided from the file's own leading bytes, not from the name or from
         * whatever the providing application claimed. A picker can be told to
         * offer PDFs and still hand over something else; the content is the only
         * thing worth believing.
         */
        fun sniff(head: ByteArray): TicketKind? = when {
            head.startsWith(0x25, 0x50, 0x44, 0x46) -> PDF
            head.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> PNG
            head.startsWith(0xFF, 0xD8, 0xFF) -> JPEG
            else -> null
        }

        private fun ByteArray.startsWith(vararg magic: Int): Boolean {
            if (size < magic.size) return false
            return magic.withIndex().all { (index, value) ->
                this[index] == (value and 0xFF).toByte()
            }
        }
    }
}

/**
 * What the wallet knows about a ticket. Deliberately thin.
 *
 * There is no passenger name, no booking reference, no barcode value and no
 * origin or destination here, because the wallet never parses the document. It
 * holds a file the person chose and shows it back to them.
 */
@Serializable
data class TicketRecord(
    val id: String,
    val label: String,
    val mediaType: String,
    val bytes: Long,
    val importedAt: String,
) {
    val kind: TicketKind?
        get() = TicketKind.entries.firstOrNull { it.mediaType == mediaType }
}

sealed interface TicketImport {
    data class Imported(val record: TicketRecord) : TicketImport
    data object UnsupportedType : TicketImport
    data class TooLarge(val limitBytes: Long) : TicketImport
    data object StorageFull : TicketImport
    data class Unreadable(val detail: String) : TicketImport
}

/**
 * Imported travel documents, encrypted at rest.
 *
 * Every file is written through [EncryptedFile], whose key lives in the Android
 * Keystore and cannot be extracted from the device. The directory is excluded
 * from cloud backup and device transfer in both backup_rules.xml and
 * data_extraction_rules.xml, and `android:allowBackup` is false, so a ticket
 * never leaves the device by a route the person did not choose.
 *
 * Nothing in this class logs a file's contents, and no method here returns
 * anything that could be put in a URL, a notification or a diagnostics report.
 */
class TicketStore(context: Context) {

    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, DIRECTORY)

    private val masterKey by lazy {
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /**
     * The index is encrypted too. A file name alone can say more than it looks
     * ("Athens-Kithra-Friday.pdf"), so the list of what is in the wallet gets
     * the same protection as the documents themselves.
     */
    private val index by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            INDEX_PREFERENCES,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun list(): List<TicketRecord> = withContext(Dispatchers.IO) {
        readIndex().sortedByDescending { it.importedAt }
    }

    /**
     * Imports one document the person picked through the Storage Access
     * Framework.
     *
     * [uri] is a one-shot grant for a single document. The application holds no
     * storage permission of any kind and never asks for one, so this is the only
     * way a file can get in.
     */
    suspend fun import(uri: Uri, nowIso: String): TicketImport = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val declaredSize = queryLength(uri)
        if (declaredSize != null && declaredSize > MAX_BYTES) {
            return@withContext TicketImport.TooLarge(MAX_BYTES)
        }

        val bytes = try {
            resolver.openInputStream(uri)?.use { stream ->
                // Read one byte past the limit so an undeclared oversized file is
                // refused instead of being truncated into a broken ticket.
                stream.readAtMost(MAX_BYTES + 1)
            } ?: return@withContext TicketImport.Unreadable("no stream")
        } catch (error: SecurityException) {
            return@withContext TicketImport.Unreadable(error.javaClass.simpleName)
        } catch (error: IOException) {
            return@withContext TicketImport.Unreadable(error.javaClass.simpleName)
        }

        if (bytes.size > MAX_BYTES) return@withContext TicketImport.TooLarge(MAX_BYTES)
        if (bytes.isEmpty()) return@withContext TicketImport.Unreadable("empty")

        val kind = TicketKind.sniff(bytes) ?: return@withContext TicketImport.UnsupportedType

        val id = newId()
        val label = queryLabel(uri) ?: defaultLabel(kind)
        val target = File(directory, id)
        directory.mkdirs()

        try {
            encryptedFile(target).openFileOutput().use { output -> output.write(bytes) }
        } catch (error: IOException) {
            target.delete()
            return@withContext if (isOutOfSpace(error)) {
                TicketImport.StorageFull
            } else {
                TicketImport.Unreadable(error.javaClass.simpleName)
            }
        } catch (error: SecurityException) {
            target.delete()
            return@withContext TicketImport.Unreadable(error.javaClass.simpleName)
        }

        val record = TicketRecord(
            id = id,
            label = label,
            mediaType = kind.mediaType,
            bytes = bytes.size.toLong(),
            importedAt = nowIso,
        )
        writeIndex(readIndex() + record)
        TicketImport.Imported(record)
    }

    /**
     * The plaintext of one ticket, in memory only.
     *
     * Returned to the screen that is about to draw it and never written
     * anywhere. When the screen goes away the array is simply garbage.
     */
    suspend fun read(id: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(directory, id)
        if (!file.isFile) return@withContext null
        runCatching {
            encryptedFile(file).openFileInput().use { it.readBytes() }
        }.getOrNull()
    }

    /**
     * A seekable descriptor over a ticket's plaintext, for `PdfRenderer`, which
     * cannot read from memory.
     *
     * The plaintext is written into the application's own cache directory under
     * a random name and the path is unlinked immediately, before this method
     * returns. From that moment the bytes have no name in the file system: only
     * the open descriptor reaches them, and they are reclaimed when it closes.
     * Nothing else on the device, and no backup, can find them.
     */
    suspend fun openForRendering(id: String): ParcelFileDescriptor? = withContext(Dispatchers.IO) {
        val plaintext = read(id) ?: return@withContext null
        val scratch = File(appContext.cacheDir, "render-" + newId())
        try {
            scratch.writeBytes(plaintext)
            val descriptor = ParcelFileDescriptor.open(
                scratch,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            scratch.delete()
            descriptor
        } catch (error: IOException) {
            scratch.delete()
            null
        }
    }

    /**
     * Deletes a ticket for real: the encrypted file is removed and the index
     * entry with it. There is no copy anywhere else, so this cannot be undone,
     * and the wallet screen says so before asking.
     */
    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(directory, id)
        val removed = !file.exists() || file.delete()
        writeIndex(readIndex().filterNot { it.id == id })
        removed
    }

    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        readIndex().sumOf { it.bytes }
    }

    private fun encryptedFile(file: File): EncryptedFile = EncryptedFile.Builder(
        appContext,
        file,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
    ).build()

    private fun readIndex(): List<TicketRecord> {
        val raw = index.getString(INDEX_KEY, null) ?: return emptyList()
        val records = runCatching {
            json.decodeFromString(ListSerializer(TicketRecord.serializer()), raw)
        }.getOrDefault(emptyList())
        // A record whose file has gone (cleared app data, a failed write) is not
        // shown as if the ticket were still there.
        return records.filter { File(directory, it.id).isFile }
    }

    private fun writeIndex(records: List<TicketRecord>) {
        index.edit()
            .putString(
                INDEX_KEY,
                json.encodeToString(ListSerializer(TicketRecord.serializer()), records),
            )
            .commit()
    }

    private fun queryLabel(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) {
                cursor.getString(column)?.take(MAX_LABEL_LENGTH)
            } else {
                null
            }
        }
    }.getOrNull()

    private fun queryLength(uri: Uri): Long? = runCatching {
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) {
                cursor.getLong(column)
            } else {
                null
            }
        }
    }.getOrNull()

    private fun defaultLabel(kind: TicketKind): String = when (kind) {
        TicketKind.PDF -> "ticket.pdf"
        TicketKind.PNG -> "ticket.png"
        TicketKind.JPEG -> "ticket.jpg"
    }

    private fun newId(): String {
        val raw = ByteArray(16)
        random.nextBytes(raw)
        return raw.joinToString("") { "%02x".format(it) }
    }

    private fun isOutOfSpace(error: IOException): Boolean {
        val text = generateSequence<Throwable>(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return "enospc" in text || "no space left" in text
    }

    private fun java.io.InputStream.readAtMost(limit: Long): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK)
        var total = 0L
        while (total <= limit) {
            val read = read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, read)
            total += read
        }
        return buffer.toByteArray()
    }

    companion object {
        const val DIRECTORY: String = "wallet"
        const val INDEX_PREFERENCES: String = "poravia_wallet_keys"
        private const val INDEX_KEY = "tickets_v1"
        private const val MAX_LABEL_LENGTH = 120
        private const val CHUNK = 64 * 1024

        /**
         * Ten megabytes. A coach ticket is a page or a barcode image; anything
         * larger is not a ticket and there is no reason to hold it encrypted on
         * someone's phone.
         */
        const val MAX_BYTES: Long = 10L * 1024L * 1024L

        private val random = SecureRandom()
    }
}
