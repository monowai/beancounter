package com.beancounter.agent.tools

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Verify that ToolSelector includes the correct tool groups
 * based on page context.
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
    fun `core tools always included`() {
        val tools = selector.selectTools(mapOf("page" to "Holdings"))
        assertThat(tools).contains(portfolioTools, positionTools, marketTools, newsTools)
    }

    @Test
    fun `independence page includes retire tools`() {
        val tools = selector.selectTools(mapOf("page" to "Independence Planning"))
        assertThat(tools).contains(retireTools)
        assertThat(tools).doesNotContain(rebalanceTools)
    }

    @Test
    fun `rebalance page includes rebalance tools`() {
        val tools = selector.selectTools(mapOf("page" to "Rebalancing"))
        assertThat(tools).contains(rebalanceTools)
        assertThat(tools).doesNotContain(retireTools)
    }

    @Test
    fun `proposed page includes event tools`() {
        val tools = selector.selectTools(mapOf("page" to "Proposed Transactions"))
        assertThat(tools).contains(eventTools)
        assertThat(tools).doesNotContain(retireTools, rebalanceTools)
    }

    @Test
    fun `no context includes all tools`() {
        val tools = selector.selectTools(null)
        assertThat(tools).contains(
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
    fun `empty context includes all tools`() {
        val tools = selector.selectTools(emptyMap())
        assertThat(tools).contains(eventTools, retireTools, rebalanceTools)
    }
}