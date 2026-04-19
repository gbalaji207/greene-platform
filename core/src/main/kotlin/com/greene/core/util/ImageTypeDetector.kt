package com.greene.core.util

/**
 * Detects image types from magic bytes (the first few raw bytes of a file).
 *
 * Supported signatures:
 * - JPEG:  `FF D8 FF`          (first 3 bytes) → `image/jpeg` / `jpg`
 * - PNG:   `89 50 4E 47`       (first 4 bytes) → `image/png`  / `png`
 * - GIF:   `47 49 46 38`       (first 4 bytes, covers GIF87a and GIF89a) → `image/gif` / `gif`
 */
object ImageTypeDetector {

    /**
     * Inspects the given [header] bytes and returns a [Pair] of
     * (contentType, fileExtension) for a recognised image format, or `null` if
     * the format is not supported.
     *
     * @param header The leading bytes of the file (ideally at least 4 bytes).
     * @return A [Pair] of content-type string to file extension string, or `null`.
     */
    fun detect(header: ByteArray): Pair<String, String>? = when {
        header.size >= 3 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte() -> "image/jpeg" to "jpg"

        header.size >= 4 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte() -> "image/png" to "png"

        header.size >= 4 &&
            header[0] == 0x47.toByte() &&
            header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() &&
            header[3] == 0x38.toByte() -> "image/gif" to "gif"

        else -> null
    }
}

