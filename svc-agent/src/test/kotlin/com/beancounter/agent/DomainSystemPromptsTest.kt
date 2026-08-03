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
    fun `every domain prompt forbids a lead-in preamble`() {
        assertThat(allPrompts)
            .allSatisfy { prompt -> assertThat(prompt).contains("No preamble") }
    }

    @Test
    fun `no-coverage tool message tells the model not to narrate the fallback`() {
        assertThat(NewsTools.NO_COVERAGE_MESSAGE)
            .contains("Do not announce")
    }
}