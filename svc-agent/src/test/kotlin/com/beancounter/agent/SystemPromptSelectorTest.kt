package com.beancounter.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SystemPromptSelectorTest {
    private val selector = SystemPromptSelector()

    @Test
    fun `news sentiment page returns the news prompt`() {
        val prompt = selector.selectFor(mapOf("page" to "News & Sentiment"))
        assertThat(prompt).isEqualTo(DomainSystemPrompts.NEWS_SENTIMENT)
    }

    @Test
    fun `independence page returns the independence prompt`() {
        val prompt = selector.selectFor(mapOf("page" to "Independence"))
        assertThat(prompt).isEqualTo(DomainSystemPrompts.INDEPENDENCE)
    }

    @Test
    fun `retire page returns the independence prompt`() {
        val prompt = selector.selectFor(mapOf("page" to "Retirement Planning"))
        assertThat(prompt).isEqualTo(DomainSystemPrompts.INDEPENDENCE)
    }

    @Test
    fun `rebalance page returns the rebalance prompt`() {
        val prompt = selector.selectFor(mapOf("page" to "Rebalancing"))
        assertThat(prompt).isEqualTo(DomainSystemPrompts.REBALANCE)
    }

    @Test
    fun `event page returns the asset prompt`() {
        val prompt = selector.selectFor(mapOf("page" to "Proposed Transactions"))
        assertThat(prompt).isEqualTo(DomainSystemPrompts.ASSET)
    }

    @Test
    fun `holdings page returns the wealth prompt`() {
        val prompt = selector.selectFor(mapOf("page" to "Holdings"))
        assertThat(prompt).isEqualTo(DomainSystemPrompts.WEALTH)
    }

    @Test
    fun `null or unknown context falls back to the general prompt`() {
        assertThat(selector.selectFor(null)).isEqualTo(DomainSystemPrompts.GENERAL)
        assertThat(selector.selectFor(emptyMap())).isEqualTo(DomainSystemPrompts.GENERAL)
        assertThat(selector.selectFor(mapOf("page" to "Settings")))
            .isEqualTo(DomainSystemPrompts.GENERAL)
    }
}