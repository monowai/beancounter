package com.beancounter.position.accumulation

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.SGD
import com.beancounter.position.Constants.Companion.TEST
import com.beancounter.position.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pins the behaviour shared across strategies in all three reporting currencies, so that the
 * strategies which differ only by TrnType, or by the MoneyValues field they accrue, stay that way.
 */
class SharedBehaviourContractTest {
    private val asset = Asset("TEST", market = Constants.US)
    private val portfolio = Portfolio(TEST, currency = NZD, base = SGD)
    private val baseRate = BigDecimal("2")
    private val portfolioRate = BigDecimal("3")
    private val amount = BigDecimal("100")
    private val tradeDate = LocalDate.of(2024, 6, 20)

    private fun trn(
        trnType: TrnType,
        quantity: BigDecimal = BigDecimal.ZERO,
        tradeAmount: BigDecimal = amount
    ) = Trn(
        asset = asset,
        trnType = trnType,
        quantity = quantity,
        tradeAmount = tradeAmount,
        tradeCurrency = USD,
        tradeBaseRate = baseRate,
        tradePortfolioRate = portfolioRate,
        tradeDate = tradeDate,
        portfolio = portfolio
    )

    private fun heldPosition(positions: Positions): Position =
        BuyBehaviour().accumulate(
            trn(TrnType.BUY, BigDecimal("50"), BigDecimal("500")),
            positions
        )

    private fun Position.reported(): List<MoneyValues> =
        listOf(
            getMoneyValues(Position.In.TRADE, USD),
            getMoneyValues(Position.In.BASE, SGD),
            getMoneyValues(Position.In.PORTFOLIO, NZD)
        )

    private fun Position.field(read: (MoneyValues) -> BigDecimal): List<BigDecimal> = reported().map(read)

    private fun assertAccrued(
        actual: List<BigDecimal>,
        tradeAmount: BigDecimal
    ) {
        assertThat(actual[0]).isEqualByComparingTo(tradeAmount)
        assertThat(actual[1]).isEqualByComparingTo(tradeAmount.multiply(baseRate))
        assertThat(actual[2]).isEqualByComparingTo(tradeAmount.multiply(portfolioRate))
    }

    @Test
    fun `reduce and sell should yield the same position in every currency`() {
        val soldPositions = Positions()
        val sold = heldPosition(soldPositions)
        SellBehaviour().accumulate(
            trn(TrnType.SELL, BigDecimal("20"), BigDecimal("300")),
            soldPositions,
            sold
        )
        val reducedPositions = Positions()
        val reduced = heldPosition(reducedPositions)
        ReduceBehaviour().accumulate(
            trn(TrnType.REDUCE, BigDecimal("20"), BigDecimal("300")),
            reducedPositions,
            reduced
        )

        assertThat(reduced.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal("30"))
        assertThat(reduced.quantityValues.getTotal())
            .isEqualByComparingTo(sold.quantityValues.getTotal())
        assertAccrued(reduced.field { it.sales }, BigDecimal("300"))
        assertAccrued(reduced.field { it.realisedGain }, BigDecimal("100"))
        assertThat(reduced.field { it.sales }).isEqualTo(sold.field { it.sales })
        assertThat(reduced.field { it.realisedGain }).isEqualTo(sold.field { it.realisedGain })
        assertThat(reduced.field { it.costValue }).isEqualTo(sold.field { it.costValue })
    }

    @ParameterizedTest
    @EnumSource(TrnType::class, names = ["SELL", "REDUCE"])
    fun `closing a position should clear value and record the date`(trnType: TrnType) {
        val positions = Positions()
        val position = heldPosition(positions)
        val behaviour = if (trnType == TrnType.SELL) SellBehaviour() else ReduceBehaviour()

        behaviour.accumulate(trn(trnType, BigDecimal("50"), BigDecimal("600")), positions, position)

        assertThat(position.dateValues.closed).isEqualTo(tradeDate)
        assertThat(position.field { it.costValue }).allSatisfy {
            assertThat(it).isEqualByComparingTo(BigDecimal.ZERO)
        }
    }

    @Test
    fun `dividend should accrue only dividends and stamp the last dividend date`() {
        val positions = Positions()
        val position = positions.getOrCreate(asset)

        DividendBehaviour().accumulate(trn(TrnType.DIVI), positions, position)

        assertAccrued(position.field { it.dividends }, amount)
        assertThat(position.field { it.expenses }).allSatisfy {
            assertThat(it).isEqualByComparingTo(BigDecimal.ZERO)
        }
        assertThat(position.dateValues.lastDividend).isEqualTo(tradeDate)
    }

    @Test
    fun `income should accrue only dividends and leave the last dividend date`() {
        val positions = Positions()
        val position = positions.getOrCreate(asset)

        IncomeBehaviour().accumulate(trn(TrnType.INCOME), positions, position)

        assertAccrued(position.field { it.dividends }, amount)
        assertThat(position.field { it.expenses }).allSatisfy {
            assertThat(it).isEqualByComparingTo(BigDecimal.ZERO)
        }
        assertThat(position.dateValues.lastDividend).isNull()
    }

    @Test
    fun `expense should accrue only expenses and leave the last dividend date`() {
        val positions = Positions()
        val position = positions.getOrCreate(asset)

        ExpenseBehaviour().accumulate(trn(TrnType.EXPENSE), positions, position)

        assertAccrued(position.field { it.expenses }, amount)
        assertThat(position.field { it.dividends }).allSatisfy {
            assertThat(it).isEqualByComparingTo(BigDecimal.ZERO)
        }
        assertThat(position.dateValues.lastDividend).isNull()
    }

    @Test
    fun `repeat amounts should accumulate rather than replace`() {
        val positions = Positions()
        val position = positions.getOrCreate(asset)

        ExpenseBehaviour().accumulate(trn(TrnType.EXPENSE), positions, position)
        ExpenseBehaviour().accumulate(trn(TrnType.EXPENSE), positions, position)

        assertAccrued(position.field { it.expenses }, amount.add(amount))
    }
}