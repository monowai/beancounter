package com.beancounter.position.accumulation

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.PROP_AVERAGE_COST
import com.beancounter.position.Constants.Companion.PROP_COST_BASIS
import com.beancounter.position.Constants.Companion.PROP_COST_VALUE
import com.beancounter.position.Constants.Companion.PROP_CURRENCY
import com.beancounter.position.Constants.Companion.PROP_SALES
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

    @Test
    fun is_CashAccumulated() {
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
                quantity = BigDecimal("-10000.00"),
                cashCurrency = NZD,
                tradeCashRate = BigDecimal.ONE,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal("0.56"),
                portfolio = portfolio
            )
        val positions = Positions(portfolio)
        val position =
            accumulator.accumulate(
                trn,
                positions
            )
        val usCost = BigDecimal("-5600.00")
        assertThat(
            position.getMoneyValues(Position.In.PORTFOLIO)
        ).hasFieldOrPropertyWithValue(
            PROP_CURRENCY,
            USD
        ).hasFieldOrPropertyWithValue(
            PROP_COST_VALUE,
            trn.quantity
        ).hasFieldOrPropertyWithValue(
            PROP_COST_BASIS,
            usCost
        ).hasFieldOrPropertyWithValue(
            PROP_AVERAGE_COST,
            trn.quantity
        ).hasFieldOrPropertyWithValue(
            PROP_SALES,
            usCost
        )

        assertThat(
            position.getMoneyValues(Position.In.BASE)
        ).hasFieldOrPropertyWithValue(
            PROP_CURRENCY,
            NZD
        ).hasFieldOrPropertyWithValue(
            PROP_COST_VALUE,
            trn.quantity
        ).hasFieldOrPropertyWithValue(
            PROP_COST_BASIS,
            trn.quantity
        ).hasFieldOrPropertyWithValue(
            PROP_SALES,
            trn.quantity
        )
    }
}