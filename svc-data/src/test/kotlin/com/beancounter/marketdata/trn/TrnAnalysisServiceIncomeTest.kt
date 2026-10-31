package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.classification.ClassificationService
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class TrnAnalysisServiceIncomeTest {
    private val trnRepository = mock<TrnRepository>()
    private val fxRateService = mock<FxRateService>()
    private val assetRepository = mock<AssetRepository>()

    private val service =
        TrnAnalysisService(
            trnRepository,
            mock<SystemUserService>(),
            fxRateService,
            assetRepository,
            mock<ClassificationService>(),
            mock<PortfolioService>()
        )

    private val usd = Currency("USD")
    private val sgd = Currency("SGD")
    private val eur = Currency("EUR")
    private val portfolio = Portfolio(id = "pf", currency = sgd, base = usd)
    private val endMonth = YearMonth.of(2026, 10)

    @BeforeEach
    fun assetsResolve() {
        whenever(assetRepository.findAllById(any<Iterable<String>>())).thenReturn(emptyList())
    }

    private fun income(
        amount: String,
        currency: Currency,
        portfolioRate: String = "1",
        baseRate: String = "1",
        code: String = "ABC"
    ) = Trn(
        trnType = TrnType.DIVI,
        asset = getTestAsset(Market("US"), code),
        tradeAmount = BigDecimal(amount),
        tradeCurrency = currency,
        tradePortfolioRate = BigDecimal(portfolioRate),
        tradeBaseRate = BigDecimal(baseRate),
        portfolio = portfolio,
        tradeDate = LocalDate.of(2026, 10, 15)
    )

    private fun held(vararg trns: Trn) {
        whenever(
            trnRepository.findIncomeByPortfoliosAndDateRange(any(), any(), any(), any())
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

    private fun report(currency: String) = service.getMonthlyIncome(1, endMonth, listOf("pf"), "assetClass", currency)

    @Test
    fun `should convert with the rate recorded on the trn when asked for portfolio currency`() {
        held(
            income("100", usd, portfolioRate = "1.30"),
            income("50", sgd)
        )

        val result = report("SGD")

        assertThat(result.currency).isEqualTo("SGD")
        assertThat(result.totalIncome).isEqualByComparingTo("180")
        assertThat(result.months.single().income).isEqualByComparingTo("180")
    }

    @Test
    fun `should convert with the recorded base rate when asked for portfolio base`() {
        held(income("100", sgd, portfolioRate = "1", baseRate = "0.75"))

        assertThat(report("USD").totalIncome).isEqualByComparingTo("75")
    }

    @Test
    fun `should convert every level of the report`() {
        held(income("100", usd, portfolioRate = "1.30"))

        val group = report("SGD").groups.single()

        assertThat(group.totalIncome).isEqualByComparingTo("130")
        assertThat(group.monthlyData.single().income).isEqualByComparingTo("130")
        assertThat(group.topContributors.single().totalIncome).isEqualByComparingTo("130")
    }

    @Test
    fun `should fall back to todays rate for a currency the portfolio does not record`() {
        held(income("100", usd, portfolioRate = "1.30", baseRate = "1"))
        todayRate(usd, eur, "0.90")

        assertThat(report("EUR").totalIncome).isEqualByComparingTo("90")
    }

    @Test
    fun `should use todays rate when the recorded rate was never stamped`() {
        held(income("100", usd, portfolioRate = "1"))
        todayRate(usd, sgd, "1.25")

        assertThat(report("SGD").totalIncome).isEqualByComparingTo("125")
    }

    @Test
    fun `should refuse to total income it cannot convert`() {
        held(income("100", usd))
        whenever(fxRateService.getRates(any<FxRequest>(), eq(""))).thenReturn(FxResponse(FxPairResults()))

        assertThatThrownBy { report("EUR") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("USD")
            .hasMessageContaining("EUR")
    }

    @Test
    fun `should not fetch rates when recorded rates cover everything`() {
        held(income("100", usd, portfolioRate = "1.30"))

        assertThat(report("SGD").totalIncome).isEqualByComparingTo("130")
        verifyNoInteractions(fxRateService)
    }

    @Test
    fun `should report an empty period in the requested currency`() {
        held()

        val result = report("SGD")

        assertThat(result.currency).isEqualTo("SGD")
        assertThat(result.totalIncome).isEqualByComparingTo("0")
    }
}