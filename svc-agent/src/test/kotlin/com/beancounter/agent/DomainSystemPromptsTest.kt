package com.beancounter.agent

import com.beancounter.agent.tools.NewsTools
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Guards the shared output invariants that every domain prompt inherits.
 *
 * The no-preamble rule exists because the News & Sentiment popup renders the
 * raw markdown in a narrow side panel — a "No live news coverage is available
 * for X ... here's a summary instead" lead-in consumed the whole first screen
 * before any content appeared.
 */
class DomainSystemPromptsTest {
    private val allPrompts =
        listOf(
            DomainSystemPrompts.GENERAL,
            DomainSystemPrompts.WEALTH,
            DomainSystemPrompts.NEWS_SENTIMENT,
            DomainSystemPrompts.ASSET_REVIEW,
            DomainSystemPrompts.ASSET,
            DomainSystemPrompts.INDEPENDENCE,
            DomainSystemPrompts.REBALANCE,
            DomainSystemPrompts.WATCHLIST,
            DomainSystemPrompts.FX_REVIEW,
            DomainSystemPrompts.HOLDING_ANALYSIS
        )

    @Test
    fun `fx review reads the rate, its trend, the macro backdrop and each leg's news`() {
        assertThat(DomainSystemPrompts.FX_REVIEW)
            .contains("getCurrentPrice")
            .contains("getFxRate")
            .contains("getRateExpectations")
            .contains("getNews")
            .contains("USD leg")
            .contains("General knowledge")
            .doesNotContain("Corporate actions")
    }

    @Test
    fun `fx review presents the pair rate as the stored daily rate, not a live quote`() {
        assertThat(DomainSystemPrompts.FX_REVIEW)
            .contains("not a live quote")
            .contains("same stored FX rates")
            .doesNotContain("ECB")
    }

    @Test
    fun `asset review stays on the asset and never looks up the user's holdings`() {
        // The review is shared across users, and its tools read no user data.
        assertThat(DomainSystemPrompts.ASSET_REVIEW)
            .doesNotContain("getHolding")
            .doesNotContain("listPortfolios")
            .contains("getAssetEventsByTicker")
    }

    @Test
    fun `prompts that ship market tools send drawdown and 52 week questions to getPriceMomentum`() {
        // A chat answered "52-week highs are not available" and could not say how far a stock
        // was below its highs, although the closes to measure it were stored.
        assertThat(
            listOf(
                DomainSystemPrompts.GENERAL,
                DomainSystemPrompts.WEALTH,
                DomainSystemPrompts.ASSET,
                DomainSystemPrompts.ASSET_REVIEW,
                DomainSystemPrompts.NEWS_SENTIMENT
            )
        ).allSatisfy { prompt ->
            assertThat(prompt)
                .contains("getPriceMomentum")
                .contains("52-week high")
                .contains("drawdown")
        }
    }

    @Test
    fun `news prompt lists its price tools instead of claiming getNews is the only tool`() {
        assertThat(DomainSystemPrompts.NEWS_SENTIMENT)
            .doesNotContain("Only `getNews` is available")
            .contains("(`getCurrentPrice`, `getPriceMomentum`)")
    }

    @Test
    fun `watchlist prompt names the 52 week high and low closes as prices`() {
        assertThat(DomainSystemPrompts.WATCHLIST)
            .contains("`high52w`")
            .contains("`low52w`")
            .contains("as prices")
    }

    @Test
    fun `prompts tell the model US listings use the US market code`() {
        assertThat(
            listOf(
                DomainSystemPrompts.GENERAL,
                DomainSystemPrompts.WEALTH,
                DomainSystemPrompts.ASSET,
                DomainSystemPrompts.ASSET_REVIEW,
                DomainSystemPrompts.NEWS_SENTIMENT
            )
        ).allSatisfy { prompt -> assertThat(prompt).contains("US listings use market `US`") }
    }

    @Test
    fun `every domain prompt forbids a lead-in preamble`() {
        assertThat(allPrompts)
            .allSatisfy { prompt -> assertThat(prompt).contains("No preamble") }
    }

    @Test
    fun `watchlist prompt reads momentum and SEC fundamentals from the tool and labels the rest`() {
        // Momentum and, for US filers, the SEC figures are measured; anything else the model
        // adds from its own knowledge must say so.
        assertThat(DomainSystemPrompts.WATCHLIST)
            .contains("getWatchlist")
            .contains("Momentum")
            .contains("Fundamentals")
            .contains("priceToEarnings")
            .contains("SEC filing")
            .contains("general knowledge")
            .contains("Not financial advice")
    }

    @Test
    fun `general prompt routes watchlist questions to getWatchlist`() {
        assertThat(DomainSystemPrompts.GENERAL).contains("getWatchlist")
    }

    @Test
    fun `watchlist prompt never sets a one-day benchmark against momentum returns`() {
        // getBenchmark carries today's change only; a 6m return against it is no comparison.
        assertThat(DomainSystemPrompts.WATCHLIST)
            .contains("single-day")
            .contains("consumer_discretionary")
            .doesNotContain("relative strength")
    }

    @Test
    fun `follow-up directive answers the question instead of regenerating the brief`() {
        assertThat(DomainSystemPrompts.FOLLOW_UP)
            .contains("Answer the latest question directly")
            .contains("Do not regenerate")
    }

    @Test
    fun `no-coverage tool message tells the model not to narrate the fallback`() {
        assertThat(NewsTools.NO_COVERAGE_MESSAGE)
            .contains("Do not announce")
    }

    @Test
    fun `wealth prompt treats xirr on young positions as annualisation noise`() {
        // A five-ETF bond sleeve opened 1-11 weeks earlier was narrated as
        // "XIRR -1.45% explained by a 28bp front-end move": the annualised
        // figure was ~-0.3% cumulative. The existing "opened days ago" rule
        // did not cover positions weeks old.
        assertThat(DomainSystemPrompts.WEALTH)
            .contains("annualisation noise")
            .contains("6 months")
    }

    @Test
    fun `wealth prompt classifies the book and gives bond books a yield yardstick`() {
        // getBenchmark only has equity scopes; a fixed-income book must be
        // measured against yield moves and judged for diversification within
        // its mandate, not against an all-asset ideal.
        assertThat(DomainSystemPrompts.WEALTH)
            .contains("Classify the book")
            .contains("fixed-income")
            .contains("within the mandate")
    }

    @Test
    fun `every domain prompt tells the model to call tools silently`() {
        // Partial mitigation for the streamed-narration bug: the model still
        // emits "let me gather..." text ahead of a tool-calls turn on some
        // providers even with this instruction, so the server-side SSE reset
        // (AgentController.sseEventsFor) is the robust fix — this bullet just
        // reduces how often it fires.
        assertThat(allPrompts)
            .allSatisfy { prompt -> assertThat(prompt).contains("Call tools silently") }
    }

    @Test
    fun `independence prompt answers for the whole Plan by default`() {
        // The model used to pick one phase and present its numbers as the user's
        // retirement. "My plan" is the aggregate: every phase, via the composite tools.
        assertThat(DomainSystemPrompts.INDEPENDENCE)
            .contains("Default to the Plan")
            .contains("listIndependencePlans")
            .contains("independencePlanId")
            .contains("never present one phase's numbers as the Plan's")
            .doesNotContain("default to the SINGLE plan")
    }

    @Test
    fun `independence prompt goes phase-level only on phase context or a named phase`() {
        assertThat(DomainSystemPrompts.INDEPENDENCE)
            .contains("Go phase-level ONLY when")
            .contains("page context carries `phaseId`")
            .contains("names a")
    }

    @Test
    fun `independence prompt reads the timeline from the Plan, not from settings`() {
        // svc-retire dropped the composite columns from /settings; the stored
        // timeline is each Plan's `phases`.
        assertThat(DomainSystemPrompts.INDEPENDENCE)
            .contains("Pass the Plan's stored `phases` unchanged")
            .doesNotContain("compositePhases")
            .doesNotContain("compositeDisplayCurrency")
            .doesNotContain("compositeExcludedPlanIds")
            .doesNotContain("compositeNarrative")
    }

    @Test
    fun `general prompt routes retirement questions through the Plan`() {
        assertThat(DomainSystemPrompts.GENERAL).contains("listIndependencePlans")
    }

    @Test
    fun `independence prompt reports an unreadable timeline instead of calling it unset`() {
        assertThat(DomainSystemPrompts.INDEPENDENCE).contains("phasesUnreadable")
    }

    @Test
    fun `aggregated positions are described with weights and a day change to quote`() {
        assertThat(DomainSystemPrompts.WEALTH)
            .doesNotContain("WITHOUT `weight`")
            .contains("dayChangePercent")
    }

    @Test
    fun `holding analysis reads the opening review and holding facts, and may cite that holding's figures`() {
        assertThat(DomainSystemPrompts.HOLDING_ANALYSIS)
            .contains("## Domain: Holding Analysis")
            .contains("Asset Review")
            .contains("getHolding")
            .contains("weight")
            .contains("average cost")
            .contains("Not financial advice")
            .contains("never recompute")
            .contains("percentages and unit prices")
            .doesNotContain("market value")
            .doesNotContain("cost basis")
            .doesNotContain("exception to the dollars rule")
    }
}