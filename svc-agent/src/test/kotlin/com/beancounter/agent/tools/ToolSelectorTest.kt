package com.beancounter.agent.tools

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Verify that ToolSelector picks the right tools per domain.
 */
class ToolSelectorTest {
    private val portfolioTools = mock<PortfolioTools>()
    private val positionTools = mock<PositionTools>()
    private val marketTools = mock<MarketTools>()
    private val newsTools = mock<NewsTools>()
    private val eventTools = mock<EventTools>()
    private val retireTools = mock<RetireTools>()
    private val rebalanceTools = mock<RebalanceTools>()

    private val selector =
        ToolSelector(
            portfolioTools,
            positionTools,
            marketTools,
            newsTools,
            eventTools,
            retireTools,
            rebalanceTools
        )

    @Test
    fun `news sentiment page only ships news tools`() {
        val tools = selector.selectTools(mapOf("page" to "News & Sentiment"))
        assertThat(tools).containsExactly(newsTools)
    }

    @Test
    fun `wealth pages ship portfolio, position, market, and news tools`() {
        val tools = selector.selectTools(mapOf("page" to "Holdings"))
        assertThat(tools).containsExactlyInAnyOrder(
            portfolioTools,
            positionTools,
            marketTools,
            newsTools
        )
        assertThat(tools).doesNotContain(retireTools, rebalanceTools, eventTools)
    }

    @Test
    fun `independence page ships wealth baseline plus retire tools`() {
        val tools = selector.selectTools(mapOf("page" to "Independence Planning"))
        assertThat(tools).containsExactlyInAnyOrder(
            portfolioTools,
            positionTools,
            marketTools,
            retireTools
        )
        assertThat(tools).doesNotContain(rebalanceTools, eventTools, newsTools)
    }

    @Test
    fun `rebalance page ships wealth baseline plus rebalance tools`() {
        val tools = selector.selectTools(mapOf("page" to "Rebalancing"))
        assertThat(tools).containsExactlyInAnyOrder(
            portfolioTools,
            positionTools,
            marketTools,
            rebalanceTools
        )
        assertThat(tools).doesNotContain(retireTools, eventTools, newsTools)
    }

    @Test
    fun `asset pages ship wealth baseline plus event and news tools`() {
        val tools = selector.selectTools(mapOf("page" to "Proposed Transactions"))
        assertThat(tools).containsExactlyInAnyOrder(
            portfolioTools,
            positionTools,
            marketTools,
            eventTools,
            newsTools
        )
        assertThat(tools).doesNotContain(retireTools, rebalanceTools)
    }

    @Test
    fun `no context ships every tool`() {
        val tools = selector.selectTools(null)
        assertThat(tools).containsExactlyInAnyOrder(
            portfolioTools,
            positionTools,
            marketTools,
            newsTools,
            eventTools,
            retireTools,
            rebalanceTools
        )
    }

    @Test
    fun `empty context ships every tool`() {
        val tools = selector.selectTools(emptyMap())
        assertThat(tools).contains(eventTools, retireTools, rebalanceTools)
    }
}