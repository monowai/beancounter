package com.beancounter.agent.screenshot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * A wide brokerage page captured at 1280 pixels has text a vision model misreads:
 * on a real 26-row Schwab page it swapped tickers and digits in most reads, and
 * enlarged 2x most reads were exact.
 */
class LegibleTest {
    private fun image(
        width: Int,
        height: Int,
        format: String
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out)
        return out.toByteArray()
    }

    private fun size(screenshot: Screenshot): Pair<Int, Int> {
        val read = ImageIO.read(screenshot.bytes.inputStream())
        return read.width to read.height
    }

    @Test
    fun `should enlarge a small screenshot until its longest side is 2560 pixels`() {
        val wide = Legible.of(Screenshot(image(1280, 714, "png"), "image/png"))
        val small = Legible.of(Screenshot(image(640, 360, "png"), "image/png"))
        val tall = Legible.of(Screenshot(image(300, 1300, "png"), "image/png"))

        assertThat(size(wide)).isEqualTo(2560 to 1428)
        assertThat(size(small)).isEqualTo(2560 to 1440)
        assertThat(size(tall)).isEqualTo(591 to 2560)
        assertThat(wide.mediaType).isEqualTo("image/png")
    }

    @Test
    fun `should send an enlarged JPEG as a PNG`() {
        val enlarged = Legible.of(Screenshot(image(800, 600, "jpg"), "image/jpeg"))

        assertThat(size(enlarged)).isEqualTo(2560 to 1920)
        assertThat(ImageFormat.of(enlarged.bytes)).isEqualTo(ImageFormat.PNG)
        assertThat(enlarged.mediaType).isEqualTo("image/png")
    }

    @Test
    fun `should leave a screenshot alone when it is already large or cannot be decoded`() {
        listOf(
            // A phone screenshot: tall, and its text already large.
            Screenshot(image(1179, 2556, "png"), "image/png"),
            Screenshot(image(10, 2048, "png"), "image/png"),
            Screenshot(byteArrayOf(1, 2, 3), "image/png"),
            Screenshot("RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray(Charsets.ISO_8859_1), "image/webp")
        ).forEach { screenshot ->
            assertThat(Legible.of(screenshot)).`as`(screenshot.mediaType).isSameAs(screenshot)
        }
    }
}