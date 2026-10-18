package pm.koc.poptv.core

import java.security.MessageDigest

object Hashing {
    private val hex = "0123456789abcdef".toCharArray()

    /** First [bytes] bytes of SHA-1 as lowercase hex (default 8 bytes = 16 hex chars). */
    fun sha1Hex(input: String, bytes: Int = 8): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val n = bytes.coerceIn(1, digest.size)
        val out = CharArray(n * 2)
        for (i in 0 until n) {
            val v = digest[i].toInt() and 0xFF
            out[i * 2] = hex[v ushr 4]
            out[i * 2 + 1] = hex[v and 0x0F]
        }
        return String(out)
    }
}
