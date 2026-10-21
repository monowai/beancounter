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
    private val holdingTools = mock<HoldingTools>()
    private val marketTools = mock<MarketTools>()
    private val newsTools = mock<NewsTools>()
    private val macroTools = mock<MacroTools>()
    private val eventTools = mock<EventTools>()
    private val assetEventTools = mock<AssetEventTools>()
    private val retireTools = mock<RetireTools>()
    private val rebalanceTools = mock<RebalanceTools>()
    private val watchlistTools = mock<WatchlistTools>()

    private val selector =
        ToolSelector(
            portfolioTools,
            positionTools,
            holdingTools,
            marketTools,
            newsTools,
            macroTools,
            eventTools,
            assetEventTools,
            retireTools,
            rebalanceTools,
            watchlistTools
        )

    /** The `@Tool` method names a tool object exposes to the model, read off its real class. */
    private fun toolNames(tools: Array<Any>): Set<String> =
        tools
            .flatMap { tool ->
                generateSequence<Class<*>>(tool.javaClass) { it.superclass }
                    .first { !it.name.contains("MockitoMock") }
                    .declaredMethods
                    .filter { it.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool::class.java) }
                    .map { it.name }
            }.toSet()

    @Test
    fun `news sentiment page ships news, market, and macro tools`() {
        // marketTools is included so the model can call getCurrentPrice
        // when grounding analyst price targets surfaced in news articles.
        // macroTools pairs with the topic-backed getMarketNews("market")
        // headlines so the model can quantify the backdrop it's reading about.
        val tools = selector.selectTools(mapOf("page" to "News & Sentiment"))
        assertThat(tools).containsExactlyInAnyOrder(newsTools, marketTools, macroTools)
    }

    @Test
    fun `watchlist page ships watchlist, market, news, and macro tools only`() {
        // Watched assets are not holdings, so the portfolio and position tools stay out.
        val tools = selector.selectTools(mapOf("page" to "Watchlist"))
        assertThat(tools).containsExactlyInAnyOrder(watchlistTools, marketTools, newsTools, macroTools)
    }

    @Test
    fun `an FX review ships market, macro and news tools but no portfolio or event tools`() {
        val tools = selector.selectTools(mapOf("page" to "Asset Review", "market" to "FX"))
        assertThat(tools).containsExactlyInAnyOrder(marketTools, macroTools, newsTools)
    }

    @Test
    fun `watchlist tools stay off other domain pages`() {
        assertThat(selector.selectTools(mapOf("page" to "Holdings"))).doesNotContain(watchlistTools)
    }

    @Test
    fun `asset review reads only the asset, never the caller's portfolios, positions or holdings`() {
        // The review is about the asset alone, so it can be cached for every user.
        val userData = toolNames(arrayOf(portfolioTools, positionTools, holdingTools, eventTools))
        val names = toolNames(selector.selectTools(mapOf("page" to "Asset Review")))

        assertThat(userData).contains("listPortfolios", "getHolding", "loadPortfolioEvents")
        assertThat(names).contains("getNews", "getAssetEventsByTicker", "getCurrentPrice", "getMacroIndicators")
        assertThat(names).doesNotContainAnyElementsOf(userData)
    }

    @Test
    fun `wealth pages ship portfolio, position, market, macro, news, and event tools`() {
        // Event tools are part of the wealth bundle so users can ask
        // "how many dividends has GOOG paid?" from holdings/portfolio pages
        // without having to navigate to a corporate-events page first.
        val tools = selector.selectTools(mapOf("page" to "Holdings"))
        assertThat(tools).containsExactlyInAnyOrder(
            portfolioTools,
            positionTools,
            holdingTools,
            marketTools,
            macroTools,
            eventTools,
            assetEventTools,
            newsTools
        )
        assertThat(tools).doesNotContain(retireTools, rebalanceTools)
    }

    @Test
    fun `independence page ships wealth baseline plus retire tools`() {
        val tools = selector.selectTools(mapOf("page" to "Independence Planning"))
        assertThat(tools).containsExactlyInAnyOrder(
            portfolioTools,
            positionTools,
            holdingTools,
            marketTools,
            macroTools,
            retireTools
        )
        assertThat(tools).doesNotContain(rebalanceTools, eventTools, assetEventTools, newsTools)
    }

    @Test
    fun `rebalance page ships wealth baseline plus rebalance tools`() {
        val tools = selector.selectTools(mapOf("page" to "Rebalancing"))
        assertThat(tools).containsExactlyInAnyOrder(
            portfolioTools,
            positionTools,
            holdingTools,
            marketTools,
            macroTools,
            rebalanceTools
        )
        assertThat(tools).doesNotContain(retireTools, eventTools, assetEventTools, newsTools)
    }

    @Test
    fun `asset pages ship wealth baseline plus event and news tools`() {
        val tools = selector.selectTools(mapOf("page" to "Proposed Transactions"))
        assertThat(tools).containsExactlyInAnyOrder(
            portfolioTools,
            positionTools,
            holdingTools,
            marketTools,
            macroTools,
            eventTools,
            assetEventTools,
            newsTools
        )
        assertThat(tools).doesNotContain(retireTools, rebalanceTools)
    }

    @Test
    fun `asset review page ships market, macro, asset event and news tools`() {
        // Asset Review is a deep-dive on a single ticker triggered from
        // the assets/lookup screen — needs market context, news, and
        // corporate-event history. No user data, no retire/rebalance.
        val tools = selector.selectTools(mapOf("page" to "Asset Review"))
        assertThat(tools).containsExactlyInAnyOrder(
            marketTools,
            macroTools,
            assetEventTools,
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
            holdingTools,
            marketTools,
            macroTools,
            newsTools,
            eventTools,
            assetEventTools,
            retireTools,
            rebalanceTools,
            watchlistTools
        )
    }

    @Test
    fun `empty context ships every tool`() {
        val tools = selector.selectTools(emptyMap())
        assertThat(tools).contains(eventTools, assetEventTools, retireTools, rebalanceTools)
    }

    @Test
    fun `the asset review prompt names exactly the tools the page registers`() {
        val registered = toolNames(selector.selectTools(mapOf("page" to "Asset Review")))
        val domain =
            com.beancounter.agent.DomainSystemPrompts.ASSET_REVIEW
                .substringAfter("## Domain: Asset Review")
        val called =
            Regex("`([a-z][A-Za-z]+)\\(")
                .findAll(domain)
                .map { it.groupValues[1] }
                .toSet()

        assertThat(registered).allSatisfy { assertThat(domain).contains("`$it") }
        assertThat(called).isSubsetOf(registered)
    }
}