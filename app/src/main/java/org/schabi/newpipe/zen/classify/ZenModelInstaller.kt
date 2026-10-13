/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipInputStream

/**
 * Installs the classifier model bundle (one zip: encoder, tokenizer, vocabulary map, heads,
 * notices) into [baseDir]/[MODEL_DIR]. Plain JVM code (unit-tested with a local source):
 * the download is hashed while it is written, the SHA-256 must match before anything is unpacked,
 * only known flat file names are accepted (no zip-slip), and the new model replaces the old one
 * only when it is complete. Nothing in the bundle is code: it is data read by the tokenizer, the
 * heads parser and ONNX Runtime.
 */
class ZenModelInstaller(private val baseDir: File) {
    class InstallException(message: String) : IOException(message)

    /** Thrown when [install] stops because `cancelled()` became true. */
    class CancelledException : IOException("cancelled")

    val modelDir: File get() = File(baseDir, MODEL_DIR)
    private val marker: File get() = File(modelDir, MARKER)

    /** Installed bundle (version, SHA-256) or null if none is complete. */
    fun installed(): Pair<String, String>? {
        if (!marker.isFile || REQUIRED.any { !File(modelDir, it).isFile }) return null
        val p = Properties()
        return try {
            marker.inputStream().use { p.load(it) }
            val v = p.getProperty("version") ?: return null
            v to (p.getProperty("sha256") ?: "")
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Streams [source] to a temporary file (SHA-256 on the fly), checks [expectedSha256] and
     * installs the bundle as [version]. [progress] gets the bytes written so far.
     */
    fun install(
        source: InputStream,
        expectedSha256: String,
        version: String,
        progress: (Long) -> Unit = {},
        cancelled: () -> Boolean = { false }
    ) {
        require(expectedSha256.length == 64) { "no SHA-256 configured" }
        baseDir.mkdirs()
        val tmp = File(baseDir, "download.tmp")
        val staging = File(baseDir, "staging")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            source.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        if (cancelled()) throw CancelledException()
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                        total += n
                        if (total > MAX_BUNDLE_BYTES) throw InstallException("bundle too large")
                        progress(total)
                    }
                }
            }
            val sha = hex(digest.digest())
            if (!sha.equals(expectedSha256, ignoreCase = true)) throw InstallException("SHA-256 mismatch: $sha")
            unpack(tmp, staging)
            File(staging, MARKER).writeText("version=$version\nsha256=${sha.lowercase()}\n")
            val old = File(baseDir, "old")
            old.deleteRecursively()
            if (modelDir.exists() && !modelDir.renameTo(old)) throw InstallException("cannot replace the old model")
            if (!staging.renameTo(modelDir)) throw InstallException("cannot move the new model into place")
            old.deleteRecursively()
        } finally {
            tmp.delete()
            staging.deleteRecursively()
        }
    }

    private fun unpack(zip: File, target: File) {
        target.deleteRecursively()
        target.mkdirs()
        val seen = HashSet<String>()
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val name = e.name
                if (e.isDirectory) continue
                if (name !in REQUIRED && name !in OPTIONAL) throw InstallException("unexpected file in bundle: $name")
                if (!seen.add(name)) throw InstallException("duplicate file in bundle: $name")
                var written = 0L
                File(target, name).outputStream().use { out ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        val n = zin.read(buf)
                        if (n < 0) break
                        written += n
                        if (written > MAX_FILE_BYTES) throw InstallException("file too large: $name")
                        out.write(buf, 0, n)
                    }
                }
            }
        }
        val missing = REQUIRED.filter { it !in seen }
        if (missing.isNotEmpty()) throw InstallException("bundle incomplete, missing $missing")
    }

    /** Removes the installed model and any leftovers. */
    fun delete() {
        modelDir.deleteRecursively()
        File(baseDir, "staging").deleteRecursively()
        File(baseDir, "old").deleteRecursively()
        File(baseDir, "download.tmp").delete()
    }

    companion object {
        const val MODEL_DIR = "model"
        const val MARKER = "installed.properties"
        const val ENCODER_FILE = "encoder_int8.onnx"
        val REQUIRED = listOf(ENCODER_FILE, ZenPipeline.TOKENIZER_FILE, ZenPipeline.VOCAB_MAP_FILE, ZenPipeline.HEADS_FILE)
        val OPTIONAL = listOf("NOTICE", "NOTICE.txt", "README.md", "yt_categories.json")
        private const val BUFFER = 64 * 1024
        private const val MAX_BUNDLE_BYTES = 200L * 1024 * 1024
        private const val MAX_FILE_BYTES = 200L * 1024 * 1024

        private const val HEX = "0123456789abcdef"

        /** Lower-case hex (locale independent). */
        fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
            bytes.forEach {
                append(HEX[(it.toInt() shr 4) and 0xF])
                append(HEX[it.toInt() and 0xF])
            }
        }
    }
}
