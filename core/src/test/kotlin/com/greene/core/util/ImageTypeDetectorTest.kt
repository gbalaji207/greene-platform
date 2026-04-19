package com.greene.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ImageTypeDetectorTest {

    // ── Test 1 — JPEG ─────────────────────────────────────────────────────────

    @Test
    fun `detect - JPEG magic bytes - returns image-jpeg and jpg`() {
        val input = ByteArray(8).also {
            it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte()
        }

        val result = ImageTypeDetector.detect(input)

        assertEquals("image/jpeg" to "jpg", result)
    }

    // ── Test 2 — PNG ──────────────────────────────────────────────────────────

    @Test
    fun `detect - PNG magic bytes - returns image-png and png`() {
        val input = ByteArray(8).also {
            it[0] = 0x89.toByte(); it[1] = 0x50.toByte()
            it[2] = 0x4E.toByte(); it[3] = 0x47.toByte()
        }

        val result = ImageTypeDetector.detect(input)

        assertEquals("image/png" to "png", result)
    }

    // ── Test 3 — GIF ──────────────────────────────────────────────────────────

    @Test
    fun `detect - GIF magic bytes - returns image-gif and gif`() {
        val input = ByteArray(8).also {
            it[0] = 0x47.toByte(); it[1] = 0x49.toByte()
            it[2] = 0x46.toByte(); it[3] = 0x38.toByte()
        }

        val result = ImageTypeDetector.detect(input)

        assertEquals("image/gif" to "gif", result)
    }

    // ── Test 4 — PDF (unsupported) ────────────────────────────────────────────

    @Test
    fun `detect - PDF magic bytes - returns null`() {
        val input = ByteArray(8).also {
            it[0] = 0x25.toByte(); it[1] = 0x50.toByte()   // %P
            it[2] = 0x44.toByte(); it[3] = 0x46.toByte()   // DF
        }

        val result = ImageTypeDetector.detect(input)

        assertNull(result)
    }

    // ── Test 5 — Empty array ──────────────────────────────────────────────────

    @Test
    fun `detect - empty byte array - returns null`() {
        val result = ImageTypeDetector.detect(ByteArray(0))

        assertNull(result)
    }

    // ── Test 6 — Two bytes only (JPEG-like but too short) ────────────────────

    @Test
    fun `detect - two bytes only FF D8 - returns null because minimum is 3 bytes`() {
        val input = byteArrayOf(0xFF.toByte(), 0xD8.toByte())

        val result = ImageTypeDetector.detect(input)

        assertNull(result)
    }

    // ── Test 7 — Exactly 3 bytes of JPEG ─────────────────────────────────────

    @Test
    fun `detect - exactly 3 bytes FF D8 FF - returns image-jpeg and jpg`() {
        val input = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        val result = ImageTypeDetector.detect(input)

        assertEquals("image/jpeg" to "jpg", result)
    }
}

