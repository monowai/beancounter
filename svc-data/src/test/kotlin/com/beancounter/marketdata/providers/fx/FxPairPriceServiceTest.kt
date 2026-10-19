package com.beancounter.marketdata.providers.fx

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.exception.SystemException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.FxHistoryPoint
import com.beancounter.marketdata.fx.FxHistoryResponse
import com.beancounter.marketdata.fx.FxRateService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A currency pair on the FX market is priced from the stored USD->X rates, so a watched pair
 * reads the same rate a portfolio valuation converts with.
 */
internal class FxPairPriceServiceTest {
    private val fxRateService = mock<FxRateService>()
    private val currencyService = mock<CurrencyService>()
    private val service = FxPairPriceService(fxRateService, currencyService)

    private val fx = Market(code = "FX", currencyId = "USD")
    private val friday = LocalDate.of(2024, 1, 19)

    @BeforeEach
    fun configuredCurrencies() {
        // Index codes sit in some currency tables; they are not currencies.
        whenever(currencyService.currencies()).thenReturn(
            listOf("AUD", "GSPC", "NZD", "SGD", "USD").map { Currency(it) }
        )
    }

    @Test
    fun `should support only the FX market`() {
        assertThat(service.isMarketSupported(Market("fx"))).isTrue()
        assertThat(service.isMarketSupported(Market("US"))).isFalse()
        assertThat(service.getId()).isEqualTo("FX")
    }

    @Test
    fun `should persist pair prices like any priced asset`() {
        assertThat(service.isApiSupported()).isTrue()
    }

    @Test
    fun `should price a pair at the base to quote rate on the date the rate is for`() {
        // Monday requested; the latest stored rates are Friday's.
        whenever(fxRateService.getPairRate("NZD", "SGD", "2024-01-22")).thenReturn(
            FxRate(from = Currency("NZD"), to = Currency("SGD"), rate = BigDecimal("0.82345678"), date = friday)
        )
        val asset = getTestAsset(fx, "NZDSGD")

        val prices = service.getMarketData(PriceRequest.of(asset, "2024-01-22"))

        assertThat(prices).hasSize(1)
        val price = prices.single()
        assertThat(price.asset.code).isEqualTo("NZDSGD")
        assertThat(price.priceDate).isEqualTo(friday)
        assertThat(price.close).isEqualByComparingTo("0.823457")
        assertThat(price.source).isEqualTo(FxPairPriceService.ID)
    }

    @Test
    fun `should skip a pair that has no rate rather than fail the batch`() {
        whenever(fxRateService.getPairRate("NZD", "SGD", "2024-01-22")).thenThrow(SystemException("no rates"))
        whenever(fxRateService.getPairRate("AUD", "SGD", "2024-01-22")).thenReturn(
            FxRate(from = Currency("AUD"), to = Currency("SGD"), rate = BigDecimal("0.88"), date = friday)
        )
        val request =
            PriceRequest(
                "2024-01-22",
                listOf(
                    PriceRequest.of(getTestAsset(fx, "NZDSGD")).assets.single(),
                    PriceRequest.of(getTestAsset(fx, "AUDSGD")).assets.single(),
                    PriceRequest.of(getTestAsset(fx, "EURX")).assets.single()
                )
            )

        val prices = service.getMarketData(request)

        assertThat(prices.map { it.asset.code }).containsExactly("AUDSGD")
    }

    @Test
    fun `should resolve the price date the way the FX rates pick theirs`() {
        whenever(fxRateService.getDate("today")).thenReturn(friday)

        assertThat(service.getDate(fx, PriceRequest(date = "today"))).isEqualTo(friday)
    }

    @Test
    fun `should backfill a pair from the stored rate history`() {
        val from = LocalDate.of(2024, 1, 1)
        whenever(fxRateService.getHistoricalRates(any(), any(), any<LocalDate>(), any<LocalDate>())).thenReturn(
            FxHistoryResponse(
                from = "NZD",
                to = "SGD",
                startDate = from,
                endDate = friday,
                data =
                    listOf(
                        FxHistoryPoint(LocalDate.of(2024, 1, 18), BigDecimal("0.82")),
                        FxHistoryPoint(friday, BigDecimal("0.83"))
                    )
            )
        )

        val response = service.backFill(getTestAsset(fx, "NZDSGD"), from)

        assertThat(response.data.map { it.priceDate }).containsExactly(LocalDate.of(2024, 1, 18), friday)
        assertThat(response.data.map { it.close })
            .usingElementComparator { a, b -> a.compareTo(b) }
            .containsExactly(BigDecimal("0.82"), BigDecimal("0.83"))
        assertThat(response.data).allSatisfy { assertThat(it.source).isEqualTo(FxPairPriceService.ID) }
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
}