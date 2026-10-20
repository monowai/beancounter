package com.beancounter.common.contracts

import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A search hit carries the same traits as an Asset, from its type and market.
 */
class AssetSearchResultJsonTest {
    private val ownerId = "abcdefghijklmnopqrstuv"

    @Test
    fun `should serialize traits for a public search hit`() {
        val hit = AssetSearchResult("BRK.B", "Berkshire", "Equity", null, "USD", market = "NYSE")

        val tree = objectMapper.readTree(objectMapper.writeValueAsString(hit))

        assertThat(tree.get("chartable").asBoolean()).isTrue()
        assertThat(tree.get("watchable").asBoolean()).isTrue()
        assertThat(tree.get("hasNews").asBoolean()).isTrue()
        assertThat(tree.get("displayCode").asString()).isEqualTo("BRK.B")
    }

    @Test
    fun `should serialize traits for a private bank account hit`() {
        val hit = AssetSearchResult("$ownerId.SAVINGS", "Savings", "Bank Account", null, "USD", market = "PRIVATE")

        val tree = objectMapper.readTree(objectMapper.writeValueAsString(hit))

        assertThat(tree.get("chartable").asBoolean()).isFalse()
        assertThat(tree.get("watchable").asBoolean()).isFalse()
        assertThat(tree.get("hasNews").asBoolean()).isFalse()
        assertThat(tree.get("displayCode").asString()).isEqualTo("SAVINGS")
    }

    @Test
    fun `should treat a hit without a market as public`() {
        val hit = AssetSearchResult("VTI", "Total Market", "ETF", null, "USD")

        assertThat(hit.watchable).isTrue()
        assertThat(hit.hasNews).isTrue()
    }

    @Test
    fun `should round trip search hit json carrying traits`() {
        val hit = AssetSearchResult("$ownerId.HOUSE", "House", "Real Estate", null, "NZD", market = "PRIVATE")

        val result = objectMapper.readValue(objectMapper.writeValueAsString(hit), AssetSearchResult::class.java)

        assertThat(result).isEqualTo(hit)
        assertThat(result.displayCode).isEqualTo("HOUSE")
    }
}