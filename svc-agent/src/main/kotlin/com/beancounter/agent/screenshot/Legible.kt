package com.beancounter.agent.screenshot

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.stream.MemoryCacheImageInputStream

/**
 * Enlarges a screenshot whose text is too small for the vision model to read
 * reliably. A wide brokerage page captured at 1280 pixels came back with swapped
 * tickers and digits in most reads; at twice the size most reads were exact. It
 * lowers the misreads, it does not end them: the user still confirms each row.
 */
internal object Legible {
    /** A screenshot whose longest side is shorter than this, in pixels, is enlarged. */
    private const val SMALL = 2048

    /** The longest side, in pixels, of an enlarged screenshot. */
    private const val TARGET = 2560

    /**
     * The screenshot as a PNG with its longest side enlarged to [TARGET] pixels
     * when it is shorter than [SMALL]. Unchanged when it is already that large or
     * is in a format that cannot be decoded here, such as WebP.
     *
     * The size is read from the image header before any pixels are decoded, so
     * a small file that claims to be huge is never decoded, and an enlarged image
     * is never larger than [TARGET] pixels square.
     */
    fun of(screenshot: Screenshot): Screenshot {
        val image =
            try {
                MemoryCacheImageInputStream(screenshot.bytes.inputStream()).use(::decodeIfSmall)
            } catch (_: IOException) {
                null
            }
        return if (image == null) screenshot else Screenshot(png(enlarged(image)), ImageFormat.PNG.mediaType)
    }

    /** The decoded image when its header says it is small; null when it is large or no reader knows its format. */
    private fun decodeIfSmall(stream: MemoryCacheImageInputStream): BufferedImage? {
        val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return null
        return try {
            reader.input = stream
            if (longestSide(reader) < SMALL) reader.read(0) else null
        } finally {
            reader.dispose()
        }
    }

    private fun longestSide(reader: ImageReader): Int = maxOf(reader.getWidth(0), reader.getHeight(0))

    private fun enlarged(image: BufferedImage): BufferedImage {
        val scale = TARGET.toDouble() / maxOf(image.width, image.height)
        val width = Math.round(image.width * scale).toInt()
        val height = Math.round(image.height * scale).toInt()
        val enlarged = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = enlarged.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.drawImage(image, 0, 0, enlarged.width, enlarged.height, null)
        } finally {
            graphics.dispose()
        }
        return enlarged
    }

    private fun png(image: BufferedImage): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }
}