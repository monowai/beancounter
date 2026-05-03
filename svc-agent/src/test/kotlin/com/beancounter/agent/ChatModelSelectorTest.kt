package com.beancounter.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Domain → model-tier routing. SMART tiers go to costly models for analytical
 * domains (asset review deep-dive, retirement projection, rebalance planning);
 * everything else stays on FAST. Default to FAST when context is missing.
 */
class ChatModelSelectorTest {
    private val tiers =
        AgentModelTiers(
            fast = "fast-id",
            smart = "smart-id",
            deep = "deep-id"
        )
    private val selector = ChatModelSelector(tiers)

    @Test
    fun `Asset Review uses SMART`() {
        assertThat(selector.selectFor(mapOf("page" to "Asset Review"))).isEqualTo("smart-id")
    }

    @Test
    fun `Independence FI uses SMART`() {
        assertThat(selector.selectFor(mapOf("page" to "Independence"))).isEqualTo("smart-id")
        assertThat(selector.selectFor(mapOf("page" to "Retirement Plan"))).isEqualTo("smart-id")
    }

    @Test
    fun `Rebalance uses SMART`() {
        assertThat(selector.selectFor(mapOf("page" to "Rebalance Wizard"))).isEqualTo("smart-id")
    }

    @Test
    fun `Holdings uses FAST`() {
        assertThat(selector.selectFor(mapOf("page" to "Holdings"))).isEqualTo("fast-id")
    }

    @Test
    fun `News and Sentiment uses FAST`() {
        assertThat(selector.selectFor(mapOf("page" to "News & Sentiment"))).isEqualTo("fast-id")
    }

    @Test
    fun `Portfolio overview uses FAST`() {
        assertThat(selector.selectFor(mapOf("page" to "Portfolios"))).isEqualTo("fast-id")
    }

    @Test
    fun `null context falls back to FAST`() {
        assertThat(selector.selectFor(null)).isEqualTo("fast-id")
    }

    @Test
    fun `empty page falls back to FAST`() {
        assertThat(selector.selectFor(mapOf("page" to ""))).isEqualTo("fast-id")
    }

    @Test
    fun `unknown page falls back to FAST`() {
        assertThat(selector.selectFor(mapOf("page" to "Settings"))).isEqualTo("fast-id")
    }

    @Test
    fun `case-insensitive page match`() {
        assertThat(selector.selectFor(mapOf("page" to "ASSET REVIEW"))).isEqualTo("smart-id")
        assertThat(selector.selectFor(mapOf("page" to "rebalance plans"))).isEqualTo("smart-id")
    }

    @Test
    fun `deepThink flag escalates to DEEP regardless of page`() {
        assertThat(selector.selectFor(null, deepThink = true)).isEqualTo("deep-id")
        assertThat(selector.selectFor(mapOf("page" to "Holdings"), deepThink = true)).isEqualTo("deep-id")
        assertThat(selector.selectFor(mapOf("page" to "Asset Review"), deepThink = true)).isEqualTo("deep-id")
    }

    @Test
    fun `deepThink false preserves existing domain routing`() {
        assertThat(selector.selectFor(mapOf("page" to "Asset Review"), deepThink = false)).isEqualTo("smart-id")
        assertThat(selector.selectFor(mapOf("page" to "Holdings"), deepThink = false)).isEqualTo("fast-id")
    }
}