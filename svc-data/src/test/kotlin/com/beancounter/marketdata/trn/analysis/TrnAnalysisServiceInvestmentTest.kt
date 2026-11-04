package com.beancounter.marketdata.trn.analysis

import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TrnRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class TrnAnalysisServiceInvestmentTest {
    private val trnRepository = mock<TrnRepository>()
    private val fxRateService = mock<FxRateService>()
    private val systemUserService = mock<SystemUserService>()

    private val service =
        TrnAnalysisService(
            trnRepository,
            systemUserService,
            mock<PortfolioService>(),
            TradeAmountFx(fxRateService)
        )

    private val usd = Currency("USD")
    private val sgd = Currency("SGD")
    private val eur = Currency("EUR")
    private val portfolio = Portfolio(id = "pf", currency = sgd, base = usd)
    private val startDate = LocalDate.of(2026, 10, 1)
    private val endDate = LocalDate.of(2026, 10, 31)

    @BeforeEach
    fun user() {
        whenever(systemUserService.getOrThrow()).thenReturn(SystemUser(id = "owner"))
    }

    private fun trade(
        trnType: TrnType,
        amount: String,
        currency: Currency,
        portfolioRate: String = "1"
    ) = Trn(
        trnType = trnType,
        asset = getTestAsset(Market("US"), "ABC"),
        tradeAmount = BigDecimal(amount),
        tradeCurrency = currency,
        tradePortfolioRate = BigDecimal(portfolioRate),
        portfolio = portfolio,
        tradeDate = LocalDate.of(2026, 10, 15)
    )

    private fun held(vararg trns: Trn) {
        whenever(
            trnRepository.findInvestmentsByPortfoliosAndDateRange(any(), any(), any(), any())
        ).thenReturn(trns.toList())
        whenever(
            trnRepository.findInvestmentsByOwnerAndDateRange(any(), any(), any(), any())
        ).thenReturn(trns.toList())
    }

    private fun todayRate(
        from: Currency,
        to: Currency,
        rate: String
    ) {
        whenever(fxRateService.getRates(any<FxRequest>(), eq(""))).thenReturn(
            FxResponse(
                FxPairResults(
                    mapOf(IsoCurrencyPair(from.code, to.code) to FxRate(from, to, BigDecimal(rate)))
                )
            )
        )
    }

    private fun noRates() {
        whenever(fxRateService.getRates(any<FxRequest>(), eq(""))).thenReturn(FxResponse(FxPairResults()))
    }

    private fun invested(currency: String) =
        service.getMonthlyInvestmentConverted(startDate, endDate, listOf("pf"), currency)

    @Test
    fun `should refuse to total an investment it cannot convert`() {
        held(trade(TrnType.BUY, "100", usd))
        noRates()

        assertThatThrownBy { invested("EUR") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("USD")
            .hasMessageContaining("EUR")
    }

    @Test
    fun `should convert an investment with the rate recorded on the trn when asked for portfolio currency`() {
        held(
            trade(TrnType.BUY, "100", usd, portfolioRate = "1.30"),
            trade(TrnType.SELL, "20", sgd)
        )
        todayRate(usd, sgd, "1.25")

        assertThat(invested("SGD")).isEqualByComparingTo("110")
    }

    @Test
    fun `should fall back to todays rate for an investment currency the portfolio does not record`() {
        held(trade(TrnType.BUY, "100", usd, portfolioRate = "1.30"))
        todayRate(usd, eur, "0.90")

        assertThat(invested("EUR")).isEqualByComparingTo("90")
    }

    @Test
    fun `should refuse to summarise transactions it cannot convert`() {
        held(trade(TrnType.BUY, "100", usd))
        noRates()

        assertThatThrownBy { service.getTransactionSummary(4, "EUR") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("No FX rate from USD to EUR")
    }

    @Test
    fun `should summarise purchases and sales with the recorded rate`() {
        held(
            trade(TrnType.BUY, "100", usd, portfolioRate = "1.30"),
            trade(TrnType.SELL, "50", usd, portfolioRate = "1.40")
        )

        val summary = service.getTransactionSummary(4, "SGD")

        assertThat(summary.totalPurchases).isEqualByComparingTo("130")
        assertThat(summary.totalSales).isEqualByComparingTo("70")
        assertThat(summary.netInvestment).isEqualByComparingTo("60")
    }
}