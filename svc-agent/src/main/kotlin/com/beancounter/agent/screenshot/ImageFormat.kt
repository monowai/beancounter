package com.beancounter.agent.screenshot

/**
 * The image formats the vision model reads, known by their first bytes. An
 * upload's declared content type is the client's say-so; the bytes are what the
 * model is paid to read.
 */
enum class ImageFormat(
    val mediaType: String,
    private val signature: Map<Int, String>
) {
    PNG("image/png", mapOf(0 to "\u0089PNG\r\n\u001a\n")),
    JPEG("image/jpeg", mapOf(0 to "ÿØÿ")),
    GIF("image/gif", mapOf(0 to "GIF8")),
    WEBP("image/webp", mapOf(0 to "RIFF", WEBP_TAG_OFFSET to "WEBP"));

    private fun matches(bytes: ByteArray): Boolean =
        signature.all { (offset, magic) ->
            val expected = magic.toByteArray(Charsets.ISO_8859_1)
            bytes.size >= offset + expected.size && expected.indices.all { bytes[offset + it] == expected[it] }
        }

    companion object {
        /** The format these bytes are in, or null when they are not an image the model reads. */
        fun of(bytes: ByteArray): ImageFormat? = entries.firstOrNull { it.matches(bytes) }
    }
}

/** A WebP file is a RIFF container whose form type follows the four-byte size. */
private const val WEBP_TAG_OFFSET = 8