package com.beancounter.position.accumulation

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.TEST
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.hundred
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for REDUCE transaction type - same as SELL but without cash impact.
 * Used for reducing positions or recording liabilities without affecting cash.
 */
@SpringBootTest(classes = [Accumulator::class])
class ReduceBehaviourTest {
    @Autowired
    private lateinit var accumulator: Accumulator

    private val buyBehaviour = BuyBehaviour()
    private val reduceBehaviour = ReduceBehaviour()
    private val asset =
        Asset(
            "TEST",
            market = Constants.US
        )
    private val fifty = BigDecimal("50.0")

    @Test
    fun `reduce decrements position quantity like sell`() {
        val positions = Positions()
        val buyDate = LocalDate.of(2024, 1, 15)
        val reduceDate = LocalDate.of(2024, 6, 20)

        // Given I buy 50 shares
        val buyTrn =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = fifty,
                tradeAmount = hundred,
                cashAmount = hundred.negate(),
                tradeDate = buyDate
            )
        val position =
            buyBehaviour.accumulate(
                buyTrn,
                positions
            )

        assertThat(position.quantityValues.getTotal()).isEqualTo(fifty)

        // When I reduce by 25 shares
        val reduceTrn =
            Trn(
                asset = asset,
                trnType = TrnType.REDUCE,
                quantity = BigDecimal("25"),
                tradeAmount = hundred,
                tradeDate = reduceDate
            )
        reduceBehaviour.accumulate(
            reduceTrn,
            positions,
            position
        )

        // Then position quantity is reduced
        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal("25"))
    }

    @Test
    fun `reduce closes position when quantity reaches zero`() {
        val positions = Positions()
        val buyDate = LocalDate.of(2024, 1, 15)
        val reduceDate = LocalDate.of(2024, 6, 20)

        // Given I buy 50 shares
        val buyTrn =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = fifty,
                tradeAmount = hundred,
                cashAmount = hundred.negate(),
                tradeDate = buyDate
            )
        val position =
            buyBehaviour.accumulate(
                buyTrn,
                positions
            )
        assertThat(position.dateValues.closed).isNull()

        // When I reduce all 50 shares
        val reduceTrn =
            Trn(
                asset = asset,
                trnType = TrnType.REDUCE,
                quantity = fifty,
                tradeAmount = BigDecimal("200"),
                tradeDate = reduceDate
            )
        reduceBehaviour.accumulate(
            reduceTrn,
            positions,
            position
        )

        // Then position is closed
        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(position.dateValues.closed).isEqualTo(reduceDate)
    }

    @Test
    fun `reduce does not impact cash positions`() {
        val portfolio =
            Portfolio(
                TEST,
                currency = USD,
                base = NZD
            )
        val trn =
            Trn(
                trnType = TrnType.REDUCE,
                asset =
                    Asset.of(
                        AssetInput.toRealEstate(
                            NZD,
                            "HZH",
                            "My House",
                            "test-user"
                        ),
                        market = Market("RE")
                    ),
                quantity = BigDecimal.ONE,
                price = BigDecimal("1000000.00"),
                tradeAmount = BigDecimal("1000000.00"),
                tradeCurrency = NZD,
                cashCurrency = NZD,
                tradeCashRate = BigDecimal.ONE,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal("0.665051"),
                portfolio = portfolio
            )
        val positions = Positions(portfolio = portfolio)

        // When I accumulate a REDUCE transaction
        accumulator.accumulate(trn, positions)

        // Then no cash position is created (only the asset position exists)
        assertThat(positions.positions).hasSize(1)
        assertThat(TrnType.isCashImpacted(TrnType.REDUCE)).isFalse()
    }

    @Test
    fun `reduce calculates realized gains`() {
        val positions = Positions()
        val buyDate = LocalDate.of(2024, 1, 15)
        val reduceDate = LocalDate.of(2024, 6, 20)

        // Given I buy 50 shares at $2 each ($100 total)
        val buyTrn =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = fifty,
                tradeAmount = hundred,
                cashAmount = hundred.negate(),
                tradeDate = buyDate
            )
        val position =
            buyBehaviour.accumulate(
                buyTrn,
                positions
            )

        // When I reduce 50 shares at $4 each ($200 total) - notional value doubled
        val reduceTrn =
            Trn(
                asset = asset,
                trnType = TrnType.REDUCE,
                quantity = fifty,
                tradeAmount = BigDecimal("200"),
                tradeDate = reduceDate
            )
        reduceBehaviour.accumulate(
            reduceTrn,
            positions,
            position
        )

        // Then realized gain is calculated ($200 - $100 = $100 gain)
        val moneyValues = position.getMoneyValues(Position.In.TRADE)
        assertThat(moneyValues.realisedGain).isEqualByComparingTo(hundred)
    }
}