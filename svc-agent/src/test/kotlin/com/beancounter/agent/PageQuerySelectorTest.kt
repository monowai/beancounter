package com.beancounter.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PageQuerySelectorTest {
    private val selector = PageQuerySelector()

    @Test
    fun `asset review asks about the ticker by name and market`() {
        val query =
            selector.queryFor(
                mapOf("page" to "Asset Review", "tickers" to "ABC", "assetName" to "Abc Corp", "market" to "NYSE")
            )

        assertThat(query?.label).isEqualTo("Asset Review")
        assertThat(query?.question).isEqualTo(
            "Produce an Asset Review for ABC (Abc Corp) listed on NYSE. " +
                "Cover company and sector context, current sentiment from recent news, " +
                "corporate-action history (dividends and splits in the last 12 months), " +
                "and qualitative risk callouts. Stay at the ticker level — do not assume " +
                "the user holds it."
        )
    }

    @Test
    fun `asset review leaves out a name and market the page did not send`() {
        val blank =
            selector.queryFor(
                mapOf(
                    "page" to "asset-review",
                    "tickers" to "ABC",
                    "assetName" to "",
                    "market" to ""
                )
            )
        val absent = selector.queryFor(mapOf("page" to "Asset Review", "tickers" to "ABC"))

        assertThat(blank?.question).startsWith("Produce an Asset Review for ABC. Cover company")
        assertThat(absent?.question).isEqualTo(blank?.question)
    }

    @Test
    fun `asset review of a currency pair asks the same question`() {
        val query = selector.queryFor(mapOf("page" to "Asset Review", "tickers" to "ABCXYZ", "market" to "FX"))

        assertThat(query?.question).startsWith("Produce an Asset Review for ABCXYZ listed on FX. ")
    }

    @Test
    fun `asset review without a ticker has no canned question`() {
        assertThat(selector.queryFor(mapOf("page" to "Asset Review", "market" to "NYSE"))).isNull()
        assertThat(selector.queryFor(mapOf("page" to "Asset Review", "tickers" to " "))).isNull()
    }

    @Test
    fun `portfolio review asks for the daily briefing`() {
        val query = selector.queryFor(mapOf("page" to "Portfolio Review", "portfolioCode" to "P1"))

        assertThat(query?.label).isEqualTo("Portfolio Review")
        assertThat(query?.question)
            .startsWith("Role: You are a financial columnist writing a daily portfolio briefing.")
            .contains("Inputs available to you:\n\t• Portfolio holdings with weights — equities")
            .contains("\t5. What to watch — One or two forward-looking items")
            .endsWith("Length: roughly 250–400 words. A morning read, not a research note.")
            .hasSize(DAILY_BRIEFING_LENGTH)
    }

    @Test
    fun `any other page has no canned question`() {
        assertThat(selector.queryFor(null)).isNull()
        assertThat(selector.queryFor(emptyMap())).isNull()
        assertThat(selector.queryFor(mapOf("page" to "Watchlist"))).isNull()
        assertThat(selector.queryFor(mapOf("page" to "Asset Review notes", "tickers" to "ABC"))).isNull()
    }

    private companion object {
        // bc-view's DAILY_BRIEFING_PROMPT, characters as sent; a drift in either copy changes this.
        const val DAILY_BRIEFING_LENGTH = 4059
    }

    @Test
    fun `asset review names its subject upper-cased for the review cache`() {
        val subject =
            selector.assetReviewSubject(
                mapOf(
                    "page" to "asset-review",
                    "tickers" to " abc ",
                    "market" to "nyse"
                )
            )

        assertThat(subject).isEqualTo(AssetReviewSubject(ticker = "ABC", market = "NYSE"))
    }

    @Test
    fun `asset review subject is blank-market when the page sent none, and absent off the page`() {
        assertThat(selector.assetReviewSubject(mapOf("page" to "Asset Review", "tickers" to "ABC")))
            .isEqualTo(AssetReviewSubject(ticker = "ABC", market = ""))
        assertThat(selector.assetReviewSubject(mapOf("page" to "Asset Review notes", "tickers" to "ABC"))).isNull()
        assertThat(selector.assetReviewSubject(mapOf("page" to "Asset Review"))).isNull()
    }
}