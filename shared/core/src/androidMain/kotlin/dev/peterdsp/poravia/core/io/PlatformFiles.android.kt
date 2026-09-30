package dev.peterdsp.poravia.core.io

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal actual object PlatformFiles {

    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun isDirectory(path: String): Boolean = File(path).isDirectory

    actual fun size(path: String): Long = File(path).let { if (it.isFile) it.length() else 0L }

    actual fun delete(path: String): Boolean = File(path).delete()

    actual fun deleteRecursively(path: String): Boolean = File(path).deleteRecursively()

    actual fun readBytes(path: String): ByteArray = File(path).readBytes()

    actual fun writeBytes(path: String, bytes: ByteArray) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    actual fun appendBytes(path: String, bytes: ByteArray) {
        val file = File(path)
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).use { stream ->
            stream.write(bytes)
            // The download can be interrupted by process death at any moment,
            // so the resume offset on disk has to be the truth.
            stream.fd.sync()
        }
    }

    actual fun move(from: String, to: String): Boolean {
        val source = File(from)
        val target = File(to)
        target.parentFile?.mkdirs()
        return try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            true
        } catch (_: AtomicMoveNotSupportedException) {
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                true
            } catch (_: IOException) {
                false
            }
        } catch (_: IOException) {
            false
        }
    }

    actual fun mkdirs(path: String): Boolean {
        val file = File(path)
        return file.isDirectory || file.mkdirs()
    }

    actual fun list(path: String): List<String> =
        File(path).listFiles()?.map { it.name }?.sorted() ?: emptyList()

    actual fun usableSpaceBytes(path: String): Long {
        val probe = generateSequence(File(path)) { it.parentFile }.firstOrNull { it.exists() }
            ?: return -1L
        return runCatching { probe.usableSpace }.getOrDefault(-1L)
    }
}
