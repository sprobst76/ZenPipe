package org.schabi.newpipe.zen.classify

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Model download/install against a local source: checksum, file whitelist, atomic replace. */
class ZenModelInstallerTest {
    private val base: File = Files.createTempDirectory("zenmodel").toFile()

    @After
    fun cleanup() {
        base.deleteRecursively()
    }

    private fun zip(entries: Map<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            entries.forEach { (name, body) ->
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun sha(b: ByteArray) = ZenModelInstaller.hex(MessageDigest.getInstance("SHA-256").digest(b))

    private fun bundle(extra: Map<String, String> = emptyMap(), tag: String = "1") = zip(
        ZenModelInstaller.REQUIRED.associateWith { "$it-$tag" } + mapOf("NOTICE" to "MIT") + extra
    )

    @Test
    fun `valid bundle is installed with marker and progress`() {
        val b = bundle()
        val inst = ZenModelInstaller(base)
        var last = 0L
        inst.install(ByteArrayInputStream(b), sha(b), "v1", progress = { last = it })
        assertEquals(b.size.toLong(), last)
        assertEquals("v1" to sha(b), inst.installed())
        ZenModelInstaller.REQUIRED.forEach { assertEquals("$it-1", File(inst.modelDir, it).readText()) }
        assertFalse(File(base, "download.tmp").exists())
        assertFalse(File(base, "staging").exists())
    }

    @Test
    fun `wrong checksum installs nothing and keeps the old model`() {
        val inst = ZenModelInstaller(base)
        val old = bundle()
        inst.install(ByteArrayInputStream(old), sha(old), "v1")
        val b = bundle(tag = "2")
        try {
            inst.install(ByteArrayInputStream(b), sha(old), "v2")
            fail()
        } catch (e: ZenModelInstaller.InstallException) {
            assertTrue(e.message!!.contains("SHA-256"))
        }
        assertEquals("v1" to sha(old), inst.installed())
        assertEquals("${ZenModelInstaller.ENCODER_FILE}-1", File(inst.modelDir, ZenModelInstaller.ENCODER_FILE).readText())
    }

    @Test
    fun `new bundle replaces the old one`() {
        val inst = ZenModelInstaller(base)
        val a = bundle()
        inst.install(ByteArrayInputStream(a), sha(a), "v1")
        val b = bundle(tag = "2")
        inst.install(ByteArrayInputStream(b), sha(b), "v2")
        assertEquals("v2" to sha(b), inst.installed())
        assertEquals("${ZenModelInstaller.ENCODER_FILE}-2", File(inst.modelDir, ZenModelInstaller.ENCODER_FILE).readText())
        assertFalse(File(base, "old").exists())
    }

    @Test
    fun `path traversal and unknown files are rejected`() {
        for (bad in listOf("../evil.so", "sub/heads.json", "classes.dex")) {
            val b = bundle(mapOf(bad to "x"))
            try {
                ZenModelInstaller(base).install(ByteArrayInputStream(b), sha(b), "v1")
                fail(bad)
            } catch (e: ZenModelInstaller.InstallException) {
                assertTrue(e.message!!, e.message!!.contains("unexpected"))
            }
            assertNull(ZenModelInstaller(base).installed())
            assertFalse(File(base.parentFile, "evil.so").exists())
        }
    }

    @Test
    fun `incomplete bundle is rejected`() {
        val b = zip(mapOf(ZenModelInstaller.ENCODER_FILE to "x"))
        try {
            ZenModelInstaller(base).install(ByteArrayInputStream(b), sha(b), "v1")
            fail()
        } catch (e: ZenModelInstaller.InstallException) {
            assertTrue(e.message!!.contains("incomplete"))
        }
        assertNull(ZenModelInstaller(base).installed())
    }

    @Test
    fun `cancel stops the download`() {
        val b = bundle()
        try {
            ZenModelInstaller(base).install(ByteArrayInputStream(b), sha(b), "v1", cancelled = { true })
            fail()
        } catch (e: ZenModelInstaller.CancelledException) {
            // expected
        }
        assertNull(ZenModelInstaller(base).installed())
        assertFalse(File(base, "download.tmp").exists())
    }

    @Test
    fun `delete removes everything`() {
        val b = bundle()
        val inst = ZenModelInstaller(base)
        inst.install(ByteArrayInputStream(b), sha(b), "v1")
        inst.delete()
        assertNull(inst.installed())
        assertFalse(inst.modelDir.exists())
    }

    @Test
    fun `hex is lower case and locale independent`() {
        assertEquals("00ff10ab", ZenModelInstaller.hex(byteArrayOf(0, -1, 16, 0xAB.toByte())))
    }
}
