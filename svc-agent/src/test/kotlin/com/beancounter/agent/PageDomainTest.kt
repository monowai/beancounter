package com.beancounter.agent

import com.beancounter.agent.tools.AssetEventTools
import com.beancounter.agent.tools.EventTools
import com.beancounter.agent.tools.HoldingTools
import com.beancounter.agent.tools.MacroTools
import com.beancounter.agent.tools.MarketTools
import com.beancounter.agent.tools.NewsTools
import com.beancounter.agent.tools.PortfolioTools
import com.beancounter.agent.tools.PositionTools
import com.beancounter.agent.tools.RebalanceTools
import com.beancounter.agent.tools.RetireTools
import com.beancounter.agent.tools.ToolSelector
import com.beancounter.agent.tools.WatchlistTools
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * The one page → domain mapping. The table holds every page string bc-view
 * and bc-mobile send today, plus near-miss spellings, and the selectors that
 * route on the page (prompt, tools, model tier) must agree on each.
 */
class PageDomainTest {
    private val clientPages =
        listOf(
            // bc-view chat pageContext.ts
            "Home" to PageDomain.GENERAL,
            "Net Worth" to PageDomain.GENERAL,
            "Portfolios" to PageDomain.WEALTH,
            "Holdings" to PageDomain.WEALTH,
            "Allocation" to PageDomain.GENERAL,
            "Proposed Transactions" to PageDomain.ASSET,
            "Transactions" to PageDomain.GENERAL,
            "Independence Planning" to PageDomain.INDEPENDENCE,
            "Independence Phase" to PageDomain.INDEPENDENCE,
            "Rebalancing" to PageDomain.REBALANCE,
            "FX Rates" to PageDomain.GENERAL,
            "Asset Lookup" to PageDomain.GENERAL,
            "News & Sentiment" to PageDomain.NEWS_SENTIMENT,
            "Settings" to PageDomain.GENERAL,
            // bc-view popups, and bc-mobile's Asset Review and Portfolio Review
            "Portfolio Review" to PageDomain.WEALTH,
            "Model Asset Insight" to PageDomain.GENERAL,
            "Asset Review" to PageDomain.ASSET_REVIEW,
            "Holding Analysis" to PageDomain.HOLDING_ANALYSIS,
            "Rebalance Execute — Asset Insight" to PageDomain.REBALANCE,
            "Watchlist" to PageDomain.WATCHLIST,
            "" to PageDomain.GENERAL
        )

    private val nearMisses =
        listOf(
            "asset review" to PageDomain.ASSET_REVIEW,
            "ASSET-REVIEW" to PageDomain.ASSET_REVIEW,
            "  Asset Review  " to PageDomain.ASSET_REVIEW,
            "Asset  Review" to PageDomain.ASSET_REVIEW,
            "Asset\tReview" to PageDomain.ASSET_REVIEW,
            "Asset Review notes" to PageDomain.ASSET_REVIEW,
            "holding-analysis" to PageDomain.HOLDING_ANALYSIS,
            "Holding   Analysis" to PageDomain.HOLDING_ANALYSIS,
            "PORTFOLIO-REVIEW" to PageDomain.WEALTH,
            "news-sentiment" to PageDomain.NEWS_SENTIMENT,
            "Market Sentiment" to PageDomain.GENERAL,
            "Retirement Planning" to PageDomain.INDEPENDENCE,
            "Rebalance Wizard" to PageDomain.REBALANCE,
            "FI" to PageDomain.INDEPENDENCE,
            "fi planning" to PageDomain.INDEPENDENCE,
            "Asset Profile" to PageDomain.GENERAL,
            "Fixed Income" to PageDomain.GENERAL
        )

    private val table = clientPages + nearMisses

    @Test
    fun `every client page and near miss resolves to its domain`() {
        assertSoftly { softly ->
            table.forEach { (page, domain) ->
                softly.assertThat(PageDomain.forPage(page)).describedAs(page).isEqualTo(domain)
                softly.assertThat(PageDomain.of(mapOf("page" to page))).describedAs(page).isEqualTo(domain)
            }
        }
    }

    @Test
    fun `no context or no page is the general domain`() {
        assertThat(PageDomain.of(null)).isEqualTo(PageDomain.GENERAL)
        assertThat(PageDomain.of(mapOf("tickers" to "SMH"))).isEqualTo(PageDomain.GENERAL)
    }

    @Test
    fun `prompt, tools and model tier agree with the domain for every page`() {
        assertSoftly { softly ->
            table.forEach { (page, domain) ->
                val context = mapOf<String, Any>("page" to page)
                softly
                    .assertThat(prompts.selectFor(context))
                    .describedAs("prompt for '$page'")
                    .isEqualTo(promptFor.getValue(domain))
                softly
                    .assertThat(tools.selectTools(context).toSet())
                    .describedAs("tools for '$page'")
                    .isEqualTo(toolsFor.getValue(domain))
                softly
                    .assertThat(models.selectFor(context))
                    .describedAs("model for '$page'")
                    .isEqualTo(if (domain.smart) "smart-id" else "fast-id")
                softly
                    .assertThat(models.reasonsFor(context))
                    .describedAs("reasoning for '$page'")
                    .isEqualTo(domain.smart)
            }
        }
    }

    @Test
    fun `an FX pair on an Asset Review page gets the FX prompt and FX tools`() {
        val context = mapOf<String, Any>("page" to "asset-review", "market" to " fx ")
        assertThat(prompts.selectFor(context)).isEqualTo(DomainSystemPrompts.FX_REVIEW)
        assertThat(tools.selectTools(context)).containsExactlyInAnyOrder(marketTools, macroTools, newsTools)
        assertThat(models.selectFor(context)).isEqualTo("smart-id")
    }

    @Test
    fun `a canned page is matched exactly, case, hyphens and spacing aside`() {
        val exact =
            listOf(
                "Asset Review" to CannedPage.ASSET_REVIEW,
                "  asset-review " to CannedPage.ASSET_REVIEW,
                "ASSET  REVIEW" to CannedPage.ASSET_REVIEW,
                "Holding Analysis" to CannedPage.HOLDING_ANALYSIS,
                "holding-analysis" to CannedPage.HOLDING_ANALYSIS,
                "Portfolio Review" to CannedPage.PORTFOLIO_REVIEW,
                "portfolio\treview" to CannedPage.PORTFOLIO_REVIEW
            )
        assertSoftly { softly ->
            exact.forEach { (page, canned) ->
                softly.assertThat(CannedPage.of(mapOf("page" to page))).describedAs(page).isEqualTo(canned)
                softly.assertThat(PageDomain.forPage(page)).describedAs(page).isEqualTo(canned.domain)
            }
        }
    }

    @Test
    fun `a merely similar page has its domain but no canned page`() {
        listOf("Asset Review notes", "Holding Analysis notes", "My Portfolio Review", "Holdings", "").forEach {
            assertThat(CannedPage.of(mapOf("page" to it))).describedAs(it).isNull()
        }
        assertThat(CannedPage.of(null)).isNull()
        assertThat(PageDomain.forPage("Asset Review notes")).isEqualTo(PageDomain.ASSET_REVIEW)
    }

    @Test
    fun `a canned page names the page its context carries`() {
        assertThat(CannedPage.entries.map { it.page })
            .containsExactly(PageQueries.ASSET_REVIEW, PageQueries.HOLDING_ANALYSIS, PageQueries.PORTFOLIO_REVIEW)
    }

    @Test
    fun `normalising a page trims, lower-cases, and folds hyphens and whitespace runs to one space`() {
        assertThat(normalisePage("  Asset-\t Review ")).isEqualTo("asset review")
        assertThat(normalisePage(null)).isEmpty()
    }

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

    private val tools =
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
    private val prompts = SystemPromptSelector()
    private val models = ChatModelSelector(AgentModelTiers(fast = "fast-id", smart = "smart-id", deep = "deep-id"))

    private val wealth = setOf<Any>(portfolioTools, positionTools, holdingTools, marketTools, macroTools)

    private val promptFor =
        mapOf(
            PageDomain.HOLDING_ANALYSIS to DomainSystemPrompts.HOLDING_ANALYSIS,
            PageDomain.WATCHLIST to DomainSystemPrompts.WATCHLIST,
            PageDomain.ASSET_REVIEW to DomainSystemPrompts.ASSET_REVIEW,
            PageDomain.NEWS_SENTIMENT to DomainSystemPrompts.NEWS_SENTIMENT,
            PageDomain.INDEPENDENCE to DomainSystemPrompts.INDEPENDENCE,
            PageDomain.REBALANCE to DomainSystemPrompts.REBALANCE,
            PageDomain.ASSET to DomainSystemPrompts.ASSET,
            PageDomain.WEALTH to DomainSystemPrompts.WEALTH,
            PageDomain.GENERAL to DomainSystemPrompts.GENERAL
        )

    private val toolsFor =
        mapOf(
            PageDomain.HOLDING_ANALYSIS to wealth + setOf(assetEventTools, newsTools),
            PageDomain.WATCHLIST to setOf(watchlistTools, marketTools, newsTools, macroTools),
            PageDomain.ASSET_REVIEW to setOf(marketTools, macroTools, assetEventTools, newsTools),
            PageDomain.NEWS_SENTIMENT to setOf(newsTools, marketTools, macroTools),
            PageDomain.INDEPENDENCE to wealth + retireTools,
            PageDomain.REBALANCE to wealth + rebalanceTools,
            PageDomain.ASSET to wealth + setOf(assetEventTools, eventTools, newsTools),
            PageDomain.WEALTH to wealth + setOf(assetEventTools, eventTools, newsTools),
            PageDomain.GENERAL to
                wealth + setOf(newsTools, assetEventTools, eventTools, retireTools, rebalanceTools, watchlistTools)
        )
}