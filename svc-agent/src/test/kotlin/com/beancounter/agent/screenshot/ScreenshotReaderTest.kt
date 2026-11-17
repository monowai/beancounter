package com.beancounter.agent.screenshot

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Reads each fixture through the reply the vision model gave for it when it was
 * recorded (`<fixture>.reply.json`), so no test here calls a model.
 */
class ScreenshotReaderTest {
    private val png = Screenshot(byteArrayOf(1, 2, 3), "image/png")

    private fun replying(reply: String) = ScreenshotReader { _, _ -> reply }

    @ParameterizedTest
    @MethodSource("fixtures")
    fun `should read what a recorded screenshot shows`(fixture: String) {
        val reader = replying(String(fixtureBytes("$fixture.reply.json")))

        assertMatches(reader.read(png), expected(fixture))
    }

    @Test
    fun `should hand the model the screenshot and an instruction of its own`() {
        val seen = mutableListOf<Pair<String, Screenshot>>()
        val reader =
            ScreenshotReader { instruction, screenshot ->
                seen += instruction to screenshot
                """{"documentType":"HOLDINGS","rows":[]}"""
            }

        reader.read(png)

        assertThat(seen).hasSize(1)
        assertThat(seen.single().second).isSameAs(png)
        assertThat(seen.single().first).contains("documentType", "accountNumber", "costBasis", "tradeDate")
    }

    @Test
    fun `should hand the model a small screenshot enlarged`() {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(1280, 714, BufferedImage.TYPE_INT_RGB), "png", out)
        var seen: Screenshot? = null
        val reader =
            ScreenshotReader { _, screenshot ->
                seen = screenshot
                """{"documentType":"HOLDINGS","rows":[]}"""
            }

        reader.read(Screenshot(out.toByteArray(), "image/png"))

        assertThat(ImageIO.read(checkNotNull(seen).bytes.inputStream()).width).isEqualTo(2560)
    }

    @Test
    fun `should keep only the digits an account number shows`() {
        val masked = replying("""{"documentType":"HOLDINGS","accountNumber":"Individual ...482","rows":[]}""")
        val dashed = replying("""{"documentType":"HOLDINGS","accountNumber":"XXXX-7731","rows":[]}""")
        val wordsOnly = replying("""{"documentType":"HOLDINGS","accountNumber":"Roth IRA","rows":[]}""")

        assertThat(masked.read(png).accountNumber).isEqualTo("482")
        assertThat(dashed.read(png).accountNumber).isEqualTo("7731")
        assertThat(wordsOnly.read(png).accountNumber).isNull()
        assertThat(
            replying("""{"documentType":"HOLDINGS","accountNumber":"401k ...9015","rows":[]}""").read(png).accountNumber
        ).isEqualTo("9015")
    }

    @Test
    fun `should read numbers as a brokerage page prints them`() {
        val reader =
            replying(
                """{"documentType":"TRANSACTIONS","totalShown":"$74,873.02","rows":[
                {"quantity":10,"price":"$141.50","amount":"-1,415.00","fees":"0.00","gainLoss":"(167.25)",
                 "marketValue":"+9,597.84","costBasis":"","costPerShare":null},
                {"quantity":"7.482913 shares","price":".5","amount":"$-20","marketValue":"0.014200 BTC",
                 "gainLoss":"+51.14%","costBasis":"12/19 · 1 Buy","costPerShare":"lots of it","fees":"n/a"}]}"""
            )

        val proposal = reader.read(png)
        val (row, units) = proposal.rows

        assertThat(proposal.totalShown).isEqualByComparingTo("74873.02")
        assertThat(row.quantity).isEqualByComparingTo("10")
        assertThat(row.price).isEqualByComparingTo("141.50")
        assertThat(row.amount).isEqualByComparingTo("-1415.00")
        assertThat(row.fees).isEqualByComparingTo("0")
        assertThat(row.gainLoss).isEqualByComparingTo("-167.25")
        assertThat(row.marketValue).isEqualByComparingTo("9597.84")
        assertThat(row.costBasis).isNull()
        assertThat(row.costPerShare).isNull()
        assertThat(units.quantity).isEqualByComparingTo("7.482913")
        assertThat(units.price).isEqualByComparingTo("0.5")
        assertThat(units.amount).isEqualByComparingTo("-20")
        assertThat(units.marketValue).isEqualByComparingTo("0.0142")
        assertThat(listOf(units.gainLoss, units.costBasis, units.costPerShare, units.fees)).containsOnlyNulls()
    }

    @Test
    fun `should read a reply wrapped in a Markdown code fence`() {
        val fence = "`".repeat(3)

        listOf(
            "${fence}json\n{\"documentType\":\"HOLDINGS\",\"rows\":[]}\n$fence",
            "$fence\n{\"documentType\":\"HOLDINGS\"}\n$fence"
        ).forEach { reply ->
            assertThat(replying(reply).read(png).documentType).`as`(reply).isEqualTo(DocumentType.HOLDINGS)
        }
    }

    @Test
    fun `should treat a blank broker name as not shown`() {
        val reader = replying("""{"documentType":"HOLDINGS","brokerName":"  ","rows":[]}""")

        assertThat(reader.read(png).brokerName).isNull()
    }

    @Test
    fun `should refuse a reply that is not a proposal`() {
        listOf(
            "I cannot read this image.",
            """{"rows":[]}""",
            """{"documentType":"RECEIPT","rows":[]}""",
            """{"documentType":"HOLDINGS","rows":[{"tradeDate":"last Tuesday"}]}"""
        ).forEach { reply ->
            assertThatThrownBy { replying(reply).read(png) }
                .`as`(reply)
                .isInstanceOf(UnreadableScreenshotException::class.java)
        }
    }

    companion object {
        @JvmStatic
        fun fixtures() = FIXTURES
    }
}