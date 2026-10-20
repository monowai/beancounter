package com.beancounter.common.model

import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Asset JSON carries the traits clients display, read only.
 */
class AssetTraitsJsonTest {
    private val ownerId = "abcdefghijklmnopqrstuv"

    private fun asset(
        code: String,
        category: String,
        market: String
    ) = Asset(
        code = code,
        market = Market(market),
        category = category
    )

    @Test
    fun `should serialize traits for a public equity`() {
        val tree = objectMapper.readTree(objectMapper.writeValueAsString(asset("BRK.B", "EQUITY", "NYSE")))

        assertThat(tree.get("chartable").asBoolean()).isTrue()
        assertThat(tree.get("watchable").asBoolean()).isTrue()
        assertThat(tree.get("hasNews").asBoolean()).isTrue()
        assertThat(tree.get("displayCode").asString()).isEqualTo("BRK.B")
        assertThat(tree.get("cashLike").asBoolean()).isFalse()
    }

    @Test
    fun `should serialize traits for a private property`() {
        val tree = objectMapper.readTree(objectMapper.writeValueAsString(asset("$ownerId.HOUSE", "RE", "PRIVATE")))

        assertThat(tree.get("chartable").asBoolean()).isFalse()
        assertThat(tree.get("watchable").asBoolean()).isFalse()
        assertThat(tree.get("hasNews").asBoolean()).isFalse()
        assertThat(tree.get("displayCode").asString()).isEqualTo("HOUSE")
        assertThat(tree.get("cashLike").asBoolean()).isFalse()
    }

    @Test
    fun `should serialize cash like for currency cash and a bank account`() {
        val cash = objectMapper.readTree(objectMapper.writeValueAsString(asset("USD", "CASH", "CASH")))
        val account =
            objectMapper.readTree(
                objectMapper.writeValueAsString(asset("$ownerId.SAVINGS", "ACCOUNT", "PRIVATE"))
            )

        assertThat(cash.get("cashLike").asBoolean()).isTrue()
        assertThat(account.get("cashLike").asBoolean()).isTrue()
    }

    @Test
    fun `should round trip asset json carrying traits`() {
        val json = objectMapper.writeValueAsString(asset("$ownerId.SAVINGS", "ACCOUNT", "PRIVATE"))

        val result = objectMapper.readValue(json, Asset::class.java)

        assertThat(result.code).isEqualTo("$ownerId.SAVINGS")
        assertThat(result.displayCode).isEqualTo("SAVINGS")
        assertThat(result.chartable).isFalse()
    }

    @Test
    fun `should ignore traits sent by a client`() {
        val json =
            """{"code":"MSFT","market":{"code":"NASDAQ"},"assetCategory":{"id":"EQUITY","name":"Equity"},
                |"chartable":false,"watchable":false,"hasNews":false,"cashLike":true,"displayCode":"X"}
            """.trimMargin()

        val result = objectMapper.readValue(json, Asset::class.java)

        assertThat(result.chartable).isTrue()
        assertThat(result.cashLike).isFalse()
        assertThat(result.displayCode).isEqualTo("MSFT")
    }
}