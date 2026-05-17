package com.beancounter.agent.tools

import com.beancounter.agent.client.AssetClient
import com.beancounter.agent.client.EventClient
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Confirms the by-ticker corporate-events tool resolves the asset via
 * [AssetClient] before delegating to [EventClient.getAssetEvents]. The
 * LLM never sees the internal asset id — only the ticker / market pair.
 */
class EventToolsTest {
    private val asset =
        Asset(
            id = ASSET_ID,
            code = TICKER,
            market = Market(code = MARKET)
        )
    private val payload = mapOf("data" to listOf(mapOf("trnType" to "DIVI")))

    @Test
    fun `getAssetEventsByTicker resolves ticker then fetches events by id`() {
        val tools = build()

        val result = tools.getAssetEventsByTicker(TICKER, MARKET)

        assertThat(result).isEqualTo(payload)
    }

    @Test
    fun `getAssetEventsByTicker defaults market to US when omitted`() {
        // Tool param is declared optional; the default arg must thread
        // through to the AssetClient as 'US' so the LLM doesn't have to
        // know BC market conventions.
        val tools = build()

        val result = tools.getAssetEventsByTicker(TICKER)

        assertThat(result).isEqualTo(payload)
    }

    @Test
    fun `getAssetEventsByTicker treats blank market as US`() {
        val tools = build()

        val result = tools.getAssetEventsByTicker(TICKER, "")

        assertThat(result).isEqualTo(payload)
    }

    private fun build(): EventTools {
        val assetClient =
            mock<AssetClient> {
                on { getAsset(MARKET, TICKER) } doReturn asset
            }
        val eventClient =
            mock<EventClient> {
                on { getAssetEvents(ASSET_ID) } doReturn payload
            }
        return EventTools(
            eventClient = eventClient,
            portfolioServiceClient = mock<PortfolioServiceClient>(),
            assetClient = assetClient
        )
    }

    companion object {
        // US-listed tickers all live under the single canonical 'US'
        // market in Beancounter — server-side aliases collapse NASDAQ /
        // NYSE / AMEX / ARCA / NASAQ / DOW / DOW JONES to it.
        private const val MARKET = "US"
        private const val TICKER = "GOOG"
        private const val ASSET_ID = "asset-goog"
    }
}