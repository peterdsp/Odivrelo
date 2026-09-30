package dev.peterdsp.poravia.core.crypto

/**
 * A pure Kotlin SHA-256 (FIPS 180-4) used to verify every downloaded pack
 * before it is installed.
 *
 * It is implemented in common code on purpose: the digest that decides whether
 * a pack may be trusted must be byte-identical on Android and iOS, and must be
 * exercised by the same tests on both. The implementation is streaming, so a
 * multi-megabyte pack is verified while it is being written rather than being
 * held in memory twice.
 *
 * It is internal so it does not appear in the generated Objective-C header: the
 * iOS application verifies nothing itself, the core does it.
 */
internal class Sha256 {

    private val state = intArrayOf(
        0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6,
        0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19,
    )
    private val buffer = ByteArray(BLOCK_BYTES)
    private val words = IntArray(64)
    private var bufferedBytes = 0
    private var totalBytes = 0L
    private var finished = false

    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Sha256 {
        check(!finished) { "digest already finalised" }
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) {
            "offset/length out of bounds"
        }
        var position = offset
        var remaining = length
        totalBytes += length.toLong()

        if (bufferedBytes > 0) {
            val take = minOf(BLOCK_BYTES - bufferedBytes, remaining)
            bytes.copyInto(buffer, bufferedBytes, position, position + take)
            bufferedBytes += take
            position += take
            remaining -= take
            if (bufferedBytes == BLOCK_BYTES) {
                compress(buffer, 0)
                bufferedBytes = 0
            }
        }

        while (remaining >= BLOCK_BYTES) {
            compress(bytes, position)
            position += BLOCK_BYTES
            remaining -= BLOCK_BYTES
        }

        if (remaining > 0) {
            bytes.copyInto(buffer, 0, position, position + remaining)
            bufferedBytes = remaining
        }
        return this
    }

    fun digest(): ByteArray {
        check(!finished) { "digest already finalised" }
        finished = true
        val bitLength = totalBytes * 8
        buffer[bufferedBytes++] = 0x80.toByte()
        if (bufferedBytes > BLOCK_BYTES - 8) {
            while (bufferedBytes < BLOCK_BYTES) buffer[bufferedBytes++] = 0
            compress(buffer, 0)
            bufferedBytes = 0
        }
        while (bufferedBytes < BLOCK_BYTES - 8) buffer[bufferedBytes++] = 0
        for (index in 7 downTo 0) {
            buffer[bufferedBytes++] = ((bitLength ushr (index * 8)) and 0xFF).toByte()
        }
        compress(buffer, 0)

        val out = ByteArray(32)
        for (index in 0 until 8) {
            val value = state[index]
            out[index * 4] = ((value ushr 24) and 0xFF).toByte()
            out[index * 4 + 1] = ((value ushr 16) and 0xFF).toByte()
            out[index * 4 + 2] = ((value ushr 8) and 0xFF).toByte()
            out[index * 4 + 3] = (value and 0xFF).toByte()
        }
        return out
    }

    fun hexDigest(): String = toHex(digest())

    private fun compress(block: ByteArray, offset: Int) {
        for (index in 0 until 16) {
            val base = offset + index * 4
            words[index] = ((block[base].toInt() and 0xFF) shl 24) or
                ((block[base + 1].toInt() and 0xFF) shl 16) or
                ((block[base + 2].toInt() and 0xFF) shl 8) or
                (block[base + 3].toInt() and 0xFF)
        }
        for (index in 16 until 64) {
            val w15 = words[index - 15]
            val w2 = words[index - 2]
            val s0 = rotateRight(w15, 7) xor rotateRight(w15, 18) xor (w15 ushr 3)
            val s1 = rotateRight(w2, 17) xor rotateRight(w2, 19) xor (w2 ushr 10)
            words[index] = words[index - 16] + s0 + words[index - 7] + s1
        }

        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]

        for (index in 0 until 64) {
            val s1 = rotateRight(e, 6) xor rotateRight(e, 11) xor rotateRight(e, 25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + K[index] + words[index]
            val s0 = rotateRight(a, 2) xor rotateRight(a, 13) xor rotateRight(a, 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }

    private fun rotateRight(value: Int, bits: Int): Int =
        (value ushr bits) or (value shl (32 - bits))

    companion object {
        private const val BLOCK_BYTES = 64

        private val K = intArrayOf(
            0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b,
            0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
            -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3,
            0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
            -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc,
            0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039,
            -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
            0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
            -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d,
            -0x2e6d17e7, -0x2966f9dc, -0x0bf1ca7b, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
            0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8,
            -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
        )

        private const val HEX = "0123456789abcdef"

        fun toHex(bytes: ByteArray): String {
            val out = StringBuilder(bytes.size * 2)
            for (byte in bytes) {
                val value = byte.toInt() and 0xFF
                out.append(HEX[value ushr 4])
                out.append(HEX[value and 0x0F])
            }
            return out.toString()
        }

        fun hex(bytes: ByteArray): String = Sha256().update(bytes).hexDigest()

        fun hex(text: String): String = hex(text.encodeToByteArray())

        /**
         * Constant-time-ish comparison of two lowercase hex digests. Digest
         * comparison here guards integrity rather than a secret, but the
         * comparison is still written not to short-circuit on the first byte.
         */
        fun digestsMatch(expected: String, actual: String): Boolean {
            val a = expected.trim().lowercase()
            val b = actual.trim().lowercase()
            if (a.length != b.length || a.isEmpty()) return false
            var difference = 0
            for (index in a.indices) {
                difference = difference or (a[index].code xor b[index].code)
            }
            return difference == 0
        }
    }
}
