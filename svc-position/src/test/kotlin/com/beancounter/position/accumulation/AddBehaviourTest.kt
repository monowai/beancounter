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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

/**
 * Adds don't impact cash but do affect cost.
 */
@SpringBootTest(classes = [Accumulator::class])
class AddBehaviourTest {
    @Autowired
    private lateinit var accumulator: Accumulator
    private val oneMillion = BigDecimal("1000000.00")

    @Test
    fun isRealEstateAdded() {
        val portfolio = Portfolio(TEST, currency = USD, base = NZD)
        val trn =
            Trn(
                trnType = TrnType.ADD,
                asset = Asset.of(AssetInput.toRealEstate(NZD, "HZH", "My House", "test-user"), market = Market("RE")),
                quantity = BigDecimal.ONE,
                price = oneMillion,
                tradeAmount = oneMillion,
                tradeCurrency = NZD,
                cashCurrency = NZD,
                tradeCashRate = BigDecimal.ONE,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal("0.665051"),
                portfolio = portfolio,
            )
        val positions = Positions(portfolio = portfolio)
        val position = accumulator.accumulate(trn, positions)

        val usCost = BigDecimal("665051.00")
        assertThat(
            position.getMoneyValues(Position.In.PORTFOLIO),
        ).hasFieldOrPropertyWithValue(Constants.PROP_CURRENCY, USD)
            .hasFieldOrPropertyWithValue(Constants.PROP_COST_VALUE, usCost)
            .hasFieldOrPropertyWithValue(Constants.PROP_COST_BASIS, usCost)
            .hasFieldOrPropertyWithValue(Constants.PROP_AVERAGE_COST, usCost)
            .hasFieldOrPropertyWithValue(Constants.PROP_PURCHASES, usCost)

        assertThat(
            position.getMoneyValues(Position.In.BASE),
        ).hasFieldOrPropertyWithValue(Constants.PROP_CURRENCY, NZD)
            .hasFieldOrPropertyWithValue(Constants.PROP_COST_VALUE, trn.tradeAmount)
            .hasFieldOrPropertyWithValue(Constants.PROP_COST_BASIS, trn.tradeAmount)
            .hasFieldOrPropertyWithValue(Constants.PROP_PURCHASES, trn.tradeAmount)

        val tradePosition = position.getMoneyValues(Position.In.TRADE)
        assertThat(
            tradePosition,
        ).hasFieldOrPropertyWithValue(Constants.PROP_CURRENCY, NZD)
            .hasFieldOrPropertyWithValue(Constants.PROP_COST_VALUE, trn.tradeAmount)
            .hasFieldOrPropertyWithValue(Constants.PROP_COST_BASIS, trn.tradeAmount)
            .hasFieldOrPropertyWithValue(Constants.PROP_PURCHASES, trn.tradeAmount)

        assertThat(position.periodicCashFlows.cashFlows).hasSize(1)
        assertEquals(oneMillion.negate().toDouble(), position.periodicCashFlows.cashFlows[0].amount, 0.001)
    }
}
