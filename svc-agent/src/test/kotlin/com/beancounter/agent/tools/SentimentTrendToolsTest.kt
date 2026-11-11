package com.beancounter.agent.tools

import com.beancounter.agent.client.AssetClient
import com.beancounter.agent.clients.NewsClient
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The two sentiment-trend tools on [NewsTools]: svc-data's daily rows in, a latest-week against
 * prior-week comparison out, with every topic, sector and ticker kept as its own entry.
 */
class SentimentTrendToolsTest {
    private val newsClient = mock<NewsClient>()
    private val assetClient = mock<AssetClient>()
    private val tools = NewsTools(newsClient, assetClient)

    private fun counts(
        date: String,
        positive: Int,
        neutral: Int,
        negative: Int
    ) = mapOf("date" to date, "positive" to positive, "neutral" to neutral, "negative" to negative, "polarity" to 0.1)

    private fun series(
        kind: String,
        name: String,
        vararg points: Map<String, Any>
    ) = mapOf("kind" to kind, "name" to name, "points" to points.toList())

    private fun stubMarket(vararg series: Map<String, Any>) {
        whenever(newsClient.getMarketSentiment(SentimentTrend.FETCH_DAYS))
            .thenReturn(mapOf("data" to series.toList()))
    }

    @Suppress("UNCHECKED_CAST")
    private fun entries(
        result: Map<String, Any>,
        key: String
    ) = result[key] as List<Map<String, Any>>

    @Suppress("UNCHECKED_CAST")
    private fun window(
        entry: Map<String, Any>,
        key: String
    ) = entry[key] as Map<String, Any>

    @Test
    fun `market trend totals the latest seven days and the seven before, per topic`() {
        stubMarket(
            series(
                "TOPIC",
                "inflation",
                counts("2026-11-01", 1, 1, 6),
                counts("2026-11-03", 0, 0, 2),
                counts("2026-11-04", 3, 1, 0),
                counts("2026-11-10", 3, 1, 2)
            )
        )

        val result = tools.getMarketSentimentTrend()

        assertThat(result["asOf"]).isEqualTo("2026-11-10")
        val inflation = entries(result, "topics").single()
        assertThat(inflation["name"]).isEqualTo("inflation")
        assertThat(window(inflation, "latest"))
            .containsEntry("positive", 6)
            .containsEntry("neutral", 2)
            .containsEntry("negative", 2)
            .containsEntry("articles", 10)
            .containsEntry("positivePct", 60)
            .containsEntry("negativePct", 20)
        assertThat(window(inflation, "prior"))
            .containsEntry("positive", 1)
            .containsEntry("negative", 8)
            .containsEntry("positivePct", 10)
            .containsEntry("negativePct", 80)
        assertThat(inflation["direction"]).isEqualTo("improving")
    }

    @Test
    fun `market trend reports whole percentages and a worded direction, never a raw balance`() {
        stubMarket(
            series("TOPIC", "bonds", counts("2026-11-02", 5, 3, 2), counts("2026-11-10", 2, 2, 6)),
            series("TOPIC", "economy", counts("2026-11-02", 3, 4, 3), counts("2026-11-10", 3, 5, 3))
        )

        val topics = entries(tools.getMarketSentimentTrend(), "topics")

        assertThat(topics[0]["direction"]).isEqualTo("souring")
        assertThat(topics[1]["direction"]).isEqualTo("steady")
        assertThat(window(topics[1], "latest")).containsEntry("positivePct", 27).containsEntry("negativePct", 27)
        topics.forEach { topic ->
            assertThat(topic).doesNotContainKeys("net", "netChange")
            assertThat(window(topic, "latest")).doesNotContainKey("net")
        }
    }

    @Test
    fun `market trend keeps topics and sectors apart and never totals across them`() {
        stubMarket(
            series("TOPIC", "inflation", counts("2026-11-10", 4, 0, 0)),
            series("TOPIC", "energy", counts("2026-11-10", 0, 0, 4)),
            series("SECTOR", "technology", counts("2026-11-09", 1, 2, 1))
        )

        val result = tools.getMarketSentimentTrend()

        assertThat(result.keys).containsExactlyInAnyOrder("asOf", "window", "topics", "sectors")
        assertThat(entries(result, "topics").map { it["name"] }).containsExactly("inflation", "energy")
        assertThat(entries(result, "sectors").map { it["name"] }).containsExactly("technology")
        assertThat(window(entries(result, "topics")[0], "latest")).containsEntry("positivePct", 100)
        assertThat(window(entries(result, "topics")[1], "latest")).containsEntry("negativePct", 100)
    }

    @Test
    fun `market trend measures every series against the newest day in any series`() {
        stubMarket(
            series("TOPIC", "inflation", counts("2026-11-10", 1, 0, 0)),
            series("SECTOR", "energy", counts("2026-11-02", 0, 0, 3))
        )

        val energy = entries(tools.getMarketSentimentTrend(), "sectors").single()

        assertThat(energy).doesNotContainKeys("latest", "direction")
        assertThat(window(energy, "prior")["negative"]).isEqualTo(3)
    }

    @Test
    fun `market trend is no_coverage when no series has a point`() {
        stubMarket(series("TOPIC", "inflation"))

        assertThat(tools.getMarketSentimentTrend()).containsEntry("status", "no_coverage")
    }

    @Test
    fun `asset trend words the article-weighted tone per ticker for each week`() {
        stubAsset("AAPL", "id-aapl")
        stubAsset("MSFT", "id-msft")
        whenever(newsClient.getAssetSentiment(listOf("id-aapl", "id-msft"), SentimentTrend.FETCH_DAYS))
            .thenReturn(
                mapOf(
                    "data" to
                        mapOf(
                            "id-aapl" to
                                listOf(
                                    mapOf("date" to "2026-11-02", "count" to 10, "normalized" to 0.8),
                                    mapOf("date" to "2026-11-09", "count" to 30, "normalized" to 0.2),
                                    mapOf("date" to "2026-11-10", "count" to 10, "normalized" to -0.6)
                                )
                        )
                )
            )

        val result = tools.getAssetSentimentTrend("AAPL, MSFT")

        assertThat(result["asOf"]).isEqualTo("2026-11-10")
        val apple = entries(result, "assets").single()
        assertThat(apple["ticker"]).isEqualTo("AAPL")
        assertThat(window(apple, "latest")).containsEntry("articles", 40).containsEntry("tone", "mixed")
        assertThat(window(apple, "prior")).containsEntry("articles", 10).containsEntry("tone", "positive")
        assertThat(apple["direction"]).isEqualTo("souring")
        assertThat(apple).doesNotContainKeys("score", "scoreChange")
        assertThat(window(apple, "latest")).doesNotContainKey("score")
    }

    @Test
    fun `asset trend skips a ticker that does not resolve and still reports the rest`() {
        stubAsset("AAPL", "id-aapl")
        whenever(assetClient.getAsset("US", "NOPE"))
            .doThrow(IllegalStateException("not found"))
        whenever(newsClient.getAssetSentiment(listOf("id-aapl"), SentimentTrend.FETCH_DAYS))
            .thenReturn(
                mapOf(
                    "data" to
                        mapOf("id-aapl" to listOf(mapOf("date" to "2026-11-10", "count" to 4, "normalized" to 0.5)))
                )
            )

        val result = tools.getAssetSentimentTrend("NOPE,AAPL")

        assertThat(entries(result, "assets").map { it["ticker"] }).containsExactly("AAPL")
    }

    @Test
    fun `asset trend resolves tickers on the named market`() {
        whenever(assetClient.getAsset("NZX", "GNE"))
            .doReturn(asset("GNE", "id-gne"))
        whenever(newsClient.getAssetSentiment(listOf("id-gne"), SentimentTrend.FETCH_DAYS))
            .thenReturn(mapOf("data" to emptyMap<String, Any>()))

        val result = tools.getAssetSentimentTrend("GNE", "NZX")

        assertThat(result).containsEntry("status", "no_coverage").containsEntry("tickers", "GNE")
    }

    @Test
    fun `asset trend is no_coverage without calling svc-data when no ticker resolves`() {
        whenever(assetClient.getAsset(any(), any()))
            .doThrow(IllegalStateException("not found"))

        val result = tools.getAssetSentimentTrend("NOPE")

        assertThat(result).containsEntry("status", "no_coverage")
        verify(newsClient, never()).getAssetSentiment(any(), any())
    }

    private fun stubAsset(
        ticker: String,
        id: String
    ) {
        whenever(assetClient.getAsset("US", ticker))
            .doReturn(asset(ticker, id))
    }

    private fun asset(
        ticker: String,
        id: String
    ) = Asset(code = ticker, id = id, name = ticker, market = Market("US"))
}