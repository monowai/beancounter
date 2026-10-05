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
            DomainSystemPrompts.REBALANCE
        )

    @Test
    fun `asset review sends own-holding questions to getHolding instead of refusing`() {
        // An Asset Review answered "I can't — no portfolio selected" when asked about a sold
        // holding in a named portfolio, although the tools to look it up were wired.
        assertThat(DomainSystemPrompts.ASSET_REVIEW)
            .contains("getHolding")
            .contains("listPortfolios")
            .doesNotContain("Stay at the ticker level")
    }

    @Test
    fun `every domain prompt forbids a lead-in preamble`() {
        assertThat(allPrompts)
            .allSatisfy { prompt -> assertThat(prompt).contains("No preamble") }
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
}