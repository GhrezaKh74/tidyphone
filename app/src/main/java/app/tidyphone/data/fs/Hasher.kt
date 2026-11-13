package app.tidyphone.data.fs

import java.io.File
import java.security.MessageDigest

object Hasher {
    /** Streaming SHA-256 as lowercase hex, or null if the file can't be read. */
    fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER).use { input ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private const val BUFFER = 64 * 1024
}
