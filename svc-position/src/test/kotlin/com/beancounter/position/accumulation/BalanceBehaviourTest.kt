package com.beancounter.position.accumulation

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.PROP_COST_BASIS
import com.beancounter.position.Constants.Companion.PROP_COST_VALUE
import com.beancounter.position.Constants.Companion.PROP_CURRENCY
import com.beancounter.position.Constants.Companion.PROP_SALES
import com.beancounter.position.Constants.Companion.SGD
import com.beancounter.position.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

/**
 * Verify behaviour around balance behaviour assumptions.
 */
@SpringBootTest(classes = [Accumulator::class])
class BalanceBehaviourTest {
    @Autowired
    private lateinit var accumulator: Accumulator

    companion object {
        private const val RUBY_CPF = "RUBY-CPF"
    }

    @Test
    fun `cash Balance with no FX`() {
        val portfolio =
            Portfolio(
                Constants.TEST,
                currency = USD,
                base = NZD
            )
        val trn =
            Trn(
                trnType = TrnType.BALANCE,
                asset = Constants.nzdCashBalance,
                quantity = BigDecimal("10000.00"),
                cashCurrency = NZD,
                tradeCashRate = BigDecimal.ONE,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                portfolio = portfolio
            )
        val positions = Positions(portfolio)
        val position =
            accumulator.accumulate(
                trn,
                positions
            )

        assertThat(
            position.getMoneyValues(Position.In.PORTFOLIO)
        ).hasFieldOrPropertyWithValue(
            PROP_CURRENCY,
            USD
        ).hasFieldOrPropertyWithValue(
            PROP_COST_VALUE,
            BigDecimal.ZERO
        ).hasFieldOrPropertyWithValue(
            PROP_SALES,
            BigDecimal.ZERO
        )

        assertThat(
            position.getMoneyValues(Position.In.BASE)
        ).hasFieldOrPropertyWithValue(
            PROP_CURRENCY,
            NZD
        ).hasFieldOrPropertyWithValue(
            PROP_COST_VALUE,
            BigDecimal.ZERO
        ).hasFieldOrPropertyWithValue(
            PROP_COST_BASIS,
            BigDecimal.ZERO
        ).hasFieldOrPropertyWithValue(
            PROP_SALES,
            BigDecimal.ZERO
        )
    }

    @Test
    fun `policy Balance tracks cost equal to tradeAmount so gain is zero`() {
        // Regression: pension/CPF positions loaded via a single BALANCE
        // transaction reported a 100% gain because CashCost reset cost
        // to zero. For non-cash assets, a BALANCE snapshot is both the
        // contribution-to-date and the current value, so cost must equal
        // tradeAmount (gain = MV - cost = 0).
        val portfolio =
            Portfolio(
                Constants.TEST,
                currency = SGD,
                base = SGD
            )
        val cpfAsset =
            Asset(
                code = RUBY_CPF,
                id = RUBY_CPF,
                name = "Ruby CPF",
                market = Market("PRIVATE"),
                priceSymbol = RUBY_CPF,
                category = AssetCategory.POLICY
            )
        val balance = BigDecimal("250000.00")
        val trn =
            Trn(
                trnType = TrnType.BALANCE,
                asset = cpfAsset,
                quantity = balance,
                tradeAmount = balance,
                cashCurrency = SGD,
                tradeCurrency = SGD,
                tradeCashRate = BigDecimal.ONE,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                portfolio = portfolio
            )
        val positions = Positions(portfolio)
        val position =
            accumulator.accumulate(
                trn,
                positions
            )

        assertThat(
            position.getMoneyValues(Position.In.PORTFOLIO)
        ).hasFieldOrPropertyWithValue(
            PROP_COST_BASIS,
            balance
        ).hasFieldOrPropertyWithValue(
            PROP_COST_VALUE,
            balance
        )
        assertThat(
            position.getMoneyValues(Position.In.BASE)
        ).hasFieldOrPropertyWithValue(
            PROP_COST_BASIS,
            balance
        ).hasFieldOrPropertyWithValue(
            PROP_COST_VALUE,
            balance
        )
    }
}