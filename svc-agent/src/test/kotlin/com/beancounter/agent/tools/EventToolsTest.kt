package com.beancounter.agent.tools

import com.beancounter.agent.client.AssetClient
import com.beancounter.agent.client.EventClient
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Confirms the by-ticker corporate-events tool resolves the asset via
 * [AssetClient] before delegating to [EventClient.getAssetEvents]. The
 * LLM never sees the internal asset id — only the ticker / market pair.
 */
class EventToolsTest {
    @Test
    fun `getAssetEventsByTicker resolves ticker then fetches events by id`() {
        val asset =
            Asset(
                id = "asset-goog",
                code = "GOOG",
                market = Market(code = "NASDAQ")
            )
        val payload = mapOf("data" to listOf(mapOf("trnType" to "DIVI")))

        val assetClient =
            mock<AssetClient> {
                on { getAsset("NASDAQ", "GOOG") } doReturn asset
            }
        val eventClient =
            mock<EventClient> {
                on { getAssetEvents("asset-goog") } doReturn payload
            }
        val tools =
            EventTools(
                eventClient = eventClient,
                portfolioServiceClient = mock<PortfolioServiceClient>(),
                assetClient = assetClient
            )

        val result = tools.getAssetEventsByTicker("GOOG", "NASDAQ")

        assertThat(result).isEqualTo(payload)
        verify(assetClient).getAsset(eq("NASDAQ"), eq("GOOG"))
        verify(eventClient).getAssetEvents(eq("asset-goog"))
    }
}