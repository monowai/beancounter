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
import org.assertj.core.api.Assertions.assertThat
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

    @Test
    fun isRealEstateAdded() {
        val portfolio = Portfolio("TEST", Constants.USD, Constants.NZD)
        val trn =
            Trn(
                trnType = TrnType.ADD,
                asset = Asset.of(AssetInput.toRealEstate(Constants.NZD, "HZH", "My House", "test-user"), market = Market("RE")),
                quantity = BigDecimal.ONE,
                price = BigDecimal("1000000.00"),
                tradeAmount = BigDecimal("1000000.00"),
                tradeCurrency = Constants.NZD,
                cashCurrency = Constants.NZD,
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
        )
            .hasFieldOrPropertyWithValue("currency", Constants.USD)
            .hasFieldOrPropertyWithValue(Constants.PROP_COST_VALUE, usCost)
            .hasFieldOrPropertyWithValue(Constants.PROP_COST_BASIS, usCost)
            .hasFieldOrPropertyWithValue("averageCost", usCost)
            .hasFieldOrPropertyWithValue(Constants.PROP_PURCHASES, usCost)

        assertThat(
            position.getMoneyValues(Position.In.BASE),
        )
            .hasFieldOrPropertyWithValue("currency", Constants.NZD)
            .hasFieldOrPropertyWithValue(Constants.PROP_COST_VALUE, trn.tradeAmount)
            .hasFieldOrPropertyWithValue(Constants.PROP_COST_BASIS, trn.tradeAmount)
            .hasFieldOrPropertyWithValue(Constants.PROP_PURCHASES, trn.tradeAmount)
    }
}
