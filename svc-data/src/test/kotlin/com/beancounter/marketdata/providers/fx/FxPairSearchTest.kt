package com.beancounter.marketdata.providers.fx

import com.beancounter.common.model.Currency
import com.beancounter.marketdata.currency.CurrencyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * A currency pair is found from the configured currencies, however the caller spells it.
 */
internal class FxPairSearchTest {
    private val currencyService = mock<CurrencyService>()
    private val service = FxPairSearch(currencyService)

    @BeforeEach
    fun configuredCurrencies() {
        // Index codes sit in some currency tables; they are not currencies.
        whenever(currencyService.currencies()).thenReturn(
            listOf("AUD", "GSPC", "NZD", "SGD", "USD").map { Currency(it) }
        )
    }

    @Test
    fun `should search the FX market however the caller spaces or cases it`() {
        assertThat(service.searchAssets("NZDSGD", " fx ").map { it.symbol }).containsExactly("NZDSGD")
    }

    @Test
    fun `should find a pair typed as six letters on an unscoped search`() {
        val results = service.searchAssets("nzdsgd", null)

        assertThat(results).hasSize(1)
        val pair = results.single()
        assertThat(pair.symbol).isEqualTo("NZDSGD")
        assertThat(pair.name).isEqualTo("NZD/SGD")
        assertThat(pair.market).isEqualTo("FX")
        assertThat(pair.currency).isEqualTo("SGD")
        assertThat(pair.type).isEqualTo("Currency")
    }

    @Test
    fun `should accept the slash, Yahoo and prefixed spellings of a pair`() {
        listOf("NZD/SGD", "NZDSGD=X", "FX:NZDSGD", "nzd sgd").forEach { keyword ->
            assertThat(service.searchAssets(keyword, "FX").map { it.symbol })
                .describedAs(keyword)
                .containsExactly("NZDSGD")
        }
    }

    @Test
    fun `should not offer a pair whose half is not a currency`() {
        assertThat(service.searchAssets("NZDGSP", "FX")).isEmpty()
        assertThat(service.searchAssets("GSPCNZ", null)).isEmpty()
        // Configured nowhere
        assertThat(service.searchAssets("NZDJPY", null)).isEmpty()
    }

    @Test
    fun `should not offer a currency paired with itself`() {
        assertThat(service.searchAssets("NZDNZD", "FX")).isEmpty()
    }

    @Test
    fun `should not search another market`() {
        assertThat(service.searchAssets("NZDSGD", "US")).isEmpty()
    }

    @Test
    fun `should expand one currency into its pairs on an FX search only`() {
        val results = service.searchAssets("nzd", "FX")

        assertThat(results.map { it.symbol }).containsExactlyInAnyOrder("NZDAUD", "NZDSGD", "NZDUSD")
        assertThat(results.map { it.currency }).containsExactlyInAnyOrder("AUD", "SGD", "USD")
        assertThat(service.searchAssets("NZD", null)).isEmpty()
    }

    @Test
    fun `should serve the markets the FX price provider prices`() {
        assertThat(service.getId()).isEqualTo(FxPairPriceService.ID)
    }
}