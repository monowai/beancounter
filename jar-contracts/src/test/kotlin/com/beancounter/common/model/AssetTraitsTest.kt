package com.beancounter.common.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * The asset facts clients display: one rule set, stated by the backend.
 */
class AssetTraitsTest {
    private val ownerId = "abcdefghijklmnopqrstuv" // 22 chars, like a SystemUser id

    @ParameterizedTest
    @ValueSource(
        strings = [
            "CASH", "RE", "REAL ESTATE", "ACCOUNT", "BANK ACCOUNT", "TRADE", "POLICY", "PENSION", "cash", "Bank Account"
        ]
    )
    fun `should not chart a category without a market price`(category: String) {
        assertThat(AssetTraits.chartable(category)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["EQUITY", "ETF", "Mutual Fund", "INDEX", "Equity"])
    fun `should chart a priced category`(category: String) {
        assertThat(AssetTraits.chartable(category)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["CASH", "ACCOUNT", "BANK ACCOUNT", "Cash"])
    fun `should treat cash and accounts as cash like`(category: String) {
        assertThat(AssetTraits.cashLike(category)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["RE", "TRADE", "POLICY", "EQUITY"])
    fun `should not treat other categories as cash like`(category: String) {
        assertThat(AssetTraits.cashLike(category)).isFalse()
    }

    @Test
    fun `should have news for a public equity`() {
        assertThat(AssetTraits.hasNews("EQUITY", "NASDAQ")).isTrue()
    }

    @Test
    fun `should have no news for cash like categories`() {
        assertThat(AssetTraits.hasNews("CASH", "CASH")).isFalse()
        assertThat(AssetTraits.hasNews("BANK ACCOUNT", "NASDAQ")).isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["RE", "REAL ESTATE", "TRADE", "POLICY", "PENSION"])
    fun `should have no news for an unpriced category on a public market`(category: String) {
        assertThat(AssetTraits.hasNews(category, "NZX")).isFalse()
    }

    @Test
    fun `should have no news on the private market`() {
        assertThat(AssetTraits.hasNews("EQUITY", "PRIVATE")).isFalse()
        assertThat(AssetTraits.hasNews("EQUITY", "private")).isFalse()
    }

    @Test
    fun `should treat an unknown market as not private`() {
        assertThat(AssetTraits.hasNews("EQUITY", null)).isTrue()
        assertThat(AssetTraits.watchable("EQUITY", null)).isTrue()
    }

    @Test
    fun `should watch a public priced asset`() {
        assertThat(AssetTraits.watchable("ETF", "NYSE")).isTrue()
    }

    @Test
    fun `should not watch a private market asset`() {
        assertThat(AssetTraits.watchable("EQUITY", "PRIVATE")).isFalse()
    }

    @Test
    fun `should not watch an unchartable category`() {
        assertThat(AssetTraits.watchable("RE", "NYSE")).isFalse()
        assertThat(AssetTraits.watchable("CASH", "CASH")).isFalse()
    }

    @Test
    fun `should keep a public dotted ticker whole`() {
        assertThat(AssetTraits.displayCode("BRK.B")).isEqualTo("BRK.B")
    }

    @Test
    fun `should strip the owner prefix from a private code`() {
        assertThat(AssetTraits.displayCode("$ownerId.HOUSE")).isEqualTo("HOUSE")
    }

    @Test
    fun `should strip only the owner prefix from a private dotted code`() {
        assertThat(AssetTraits.displayCode("$ownerId.BRK.B")).isEqualTo("BRK.B")
    }

    @Test
    fun `should keep a code without a dot`() {
        assertThat(AssetTraits.displayCode("MSFT")).isEqualTo("MSFT")
    }

    @Test
    fun `should read an owned code after a short owner id`() {
        assertThat(AssetTraits.ownedCode("test-user.BRK.B")).isEqualTo("BRK.B")
    }
}