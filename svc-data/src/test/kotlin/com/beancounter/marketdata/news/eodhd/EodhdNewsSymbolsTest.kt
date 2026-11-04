package com.beancounter.marketdata.news.eodhd

import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * [EodhdNewsSymbols] turns caller input into the keys news is stored and fetched under. Ticker
 * keys must stay exchange-qualified, and topic keys must fit the VARCHAR(32) ticker columns.
 */
internal class EodhdNewsSymbolsTest {
    private val symbols = EodhdNewsSymbols(EodhdConfig(mock<MarketService>()), mock<AssetFinder>())

    @Test
    fun `tickers that do not look like tickers are dropped before resolution`() {
        assertThat(symbols.forTickers(" aapl ,not a ticker,,TOOLONGTICKER1", null))
            .containsExactly("AAPL.US")
    }

    @Test
    fun `an unknown market code is used verbatim as the exchange suffix`() {
        assertThat(symbols.forTickers("ABC", "ZZ")).containsExactly("ABC.ZZ")
    }

    @Test
    fun `market symbols are normalised but not re-suffixed`() {
        assertThat(symbols.forMarket(listOf(" gspc.indx ", "", "XLK.US")))
            .containsExactly("GSPC.INDX", "XLK.US")
    }

    @Test
    fun `topic keys fit the ticker column and remember the untruncated topic`() {
        val topic = "a very long topic that exceeds the column width"

        val keyToTopic = symbols.forTopics(listOf(topic, "  "))

        val key = keyToTopic.keys.single()
        assertThat(key).hasSize(32).startsWith("TOPIC:A_VERY_LONG")
        assertThat(EodhdNewsSymbols.isTopicKey(key)).isTrue()
        assertThat(EodhdNewsSymbols.topicTagFor(key, keyToTopic)).isEqualTo(topic)
    }

    @Test
    fun `a topic tag falls back to the key when the original topic is not known`() {
        assertThat(EodhdNewsSymbols.topicTagFor("TOPIC:STOCK_MARKETS", emptyMap())).isEqualTo("stock markets")
        assertThat(EodhdNewsSymbols.isTopicKey("AAPL.US")).isFalse()
    }
}