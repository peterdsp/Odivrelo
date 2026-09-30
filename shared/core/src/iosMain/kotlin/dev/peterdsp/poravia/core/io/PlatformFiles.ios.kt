package dev.peterdsp.poravia.core.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal actual object PlatformFiles {

    private val manager: NSFileManager get() = NSFileManager.defaultManager

    actual fun exists(path: String): Boolean = manager.fileExistsAtPath(path)

    actual fun isDirectory(path: String): Boolean {
        val attributes = manager.attributesOfItemAtPath(path, null) ?: return false
        return attributes[NSFileType] == NSFileTypeDirectory
    }

    actual fun size(path: String): Long {
        val attributes = manager.attributesOfItemAtPath(path, null) ?: return 0L
        val size = attributes["NSFileSize"] as? NSNumber ?: return 0L
        return size.longLongValue
    }

    actual fun delete(path: String): Boolean = manager.removeItemAtPath(path, null)

    actual fun deleteRecursively(path: String): Boolean = manager.removeItemAtPath(path, null)

    actual fun readBytes(path: String): ByteArray {
        val data = NSData.dataWithContentsOfFile(path) ?: return ByteArray(0)
        return data.toByteArray()
    }

    actual fun writeBytes(path: String, bytes: ByteArray) {
        mkdirs(Paths.parentOf(path))
        bytes.toNSData().writeToFile(path, atomically = true)
    }

    actual fun appendBytes(path: String, bytes: ByteArray) {
        if (bytes.isEmpty()) {
            if (!exists(path)) writeBytes(path, bytes)
            return
        }
        if (!exists(path)) {
            writeBytes(path, bytes)
            return
        }
        val handle = NSFileHandle.fileHandleForWritingAtPath(path) ?: run {
            writeBytes(path, readBytes(path) + bytes)
            return
        }
        handle.seekToEndOfFile()
        // The download can be interrupted at any moment, so the byte count on
        // disk has to be the truth a later resume reads back.
        handle.writeData(bytes.toNSData(), null)
        handle.synchronizeAndReturnError(null)
        handle.closeAndReturnError(null)
    }

    actual fun move(from: String, to: String): Boolean {
        mkdirs(Paths.parentOf(to))
        // A pack is installed by writing a temporary file and then replacing the
        // target, so the target may already hold the previous good copy.
        if (manager.fileExistsAtPath(to) && !manager.removeItemAtPath(to, null)) {
            return false
        }
        return manager.moveItemAtPath(from, toPath = to, error = null)
    }

    actual fun mkdirs(path: String): Boolean {
        if (isDirectory(path)) return true
        return manager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    actual fun list(path: String): List<String> {
        val contents = manager.contentsOfDirectoryAtPath(path, null) ?: return emptyList()
        return contents.mapNotNull { it as? String }.sorted()
    }

    actual fun usableSpaceBytes(path: String): Long {
        var probe = path
        repeat(8) {
            if (exists(probe)) {
                val attributes = manager.attributesOfFileSystemForPath(probe, null)
                val free = attributes?.get(NSFileSystemFreeSize) as? NSNumber
                if (free != null) return free.longLongValue
            }
            val parent = Paths.parentOf(probe)
            if (parent == probe) return -1L
            probe = parent
        }
        return -1L
    }

    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        if (length == 0) return ByteArray(0)
        val out = ByteArray(length)
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
        return out
    }

    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) return NSData()
        return usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }
}
