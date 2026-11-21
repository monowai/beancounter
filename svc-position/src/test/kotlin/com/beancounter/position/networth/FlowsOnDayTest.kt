package com.beancounter.position.networth

import com.beancounter.common.model.Currency
import com.beancounter.common.model.DayFlows
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * [FlowsOnDay] reads the external cash flows dated the valuation date, signed and sized
 * as time-weighted return reads them.
 */
class FlowsOnDayTest {
    private val dateUtils = DateUtils()
    private val flowsOnDay = FlowsOnDay(dateUtils)
    private val asAt = "2024-01-15"
    private val day = LocalDate.parse(asAt)
    private val portfolio = TestHelpers.createTestPortfolio("flows-pf", currencyCode = "USD", baseCurrency = "USD")

    @Test
    fun `money out today counts out, money in today counts in`() {
        val trns =
            listOf(
                trn(TrnType.DEDUCTION, "10"),
                trn(TrnType.EXPENSE, "20"),
                trn(TrnType.WITHDRAWAL, "30"),
                trn(TrnType.DEPOSIT, "100"),
                trn(TrnType.INCOME, "50")
            )

        val flows = flowsOnDay.of(portfolio, trns, asAt)

        assertThat(flows.inflow).isEqualByComparingTo("150")
        assertThat(flows.outflow).isEqualByComparingTo("-60")
    }

    @Test
    fun `trades, dividends and in-kind transfers are not external flows`() {
        val trns =
            listOf(
                trn(TrnType.BUY, "10"),
                trn(TrnType.SELL, "10"),
                trn(TrnType.DIVI, "10"),
                trn(TrnType.ADD, "10")
            )

        assertThat(flowsOnDay.of(portfolio, trns, asAt)).isEqualTo(DayFlows.NONE)
    }

    @Test
    fun `a flow dated yesterday is not today's`() {
        val trns = listOf(trn(TrnType.DEPOSIT, "100", day.minusDays(1)), trn(TrnType.EXPENSE, "5"))

        val flows = flowsOnDay.of(portfolio, trns, asAt)

        assertThat(flows.inflow).isEqualByComparingTo("0")
        assertThat(flows.outflow).isEqualByComparingTo("-5")
    }

    @Test
    fun `today resolves through DateUtils`() {
        val today = dateUtils.getFormattedDate(DateUtils.TODAY)
        val trns = listOf(trn(TrnType.DEPOSIT, "100", today), trn(TrnType.DEPOSIT, "7", today.minusDays(1)))

        assertThat(flowsOnDay.of(portfolio, trns, DateUtils.TODAY).inflow).isEqualByComparingTo("100")
    }

    @Test
    fun `a flow in a foreign trade currency is read in portfolio currency at the trn's rate`() {
        val trn =
            Trn(
                trnType = TrnType.EXPENSE,
                tradeDate = day,
                asset = TestHelpers.createTestAsset("NZD", "CASH"),
                quantity = BigDecimal("100"),
                tradeCurrency = Currency("NZD"),
                tradePortfolioRate = BigDecimal("0.6")
            )

        assertThat(flowsOnDay.of(portfolio, listOf(trn), asAt).outflow).isEqualByComparingTo("-60")
    }

    @Test
    fun `no trns, no flows`() {
        assertThat(flowsOnDay.of(portfolio, emptyList(), asAt)).isEqualTo(DayFlows.NONE)
    }

    private fun trn(
        type: TrnType,
        amount: String,
        tradeDate: LocalDate = day,
        owner: Portfolio = portfolio
    ): Trn =
        Trn(
            trnType = type,
            tradeDate = tradeDate,
            asset = TestHelpers.createTestAsset("USD", "CASH"),
            portfolio = owner,
            quantity = BigDecimal(amount),
            tradeCurrency = owner.currency
        )
}