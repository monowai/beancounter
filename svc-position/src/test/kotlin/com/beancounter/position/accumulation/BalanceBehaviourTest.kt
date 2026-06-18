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

    private fun cpfPolicyAsset() =
        Asset(
            code = RUBY_CPF,
            id = RUBY_CPF,
            name = "Ruby CPF",
            market = Market("PRIVATE"),
            priceSymbol = RUBY_CPF,
            category = AssetCategory.POLICY
        )

    private fun balanceTrn(
        portfolio: Portfolio,
        asset: Asset,
        qty: BigDecimal,
        contribution: BigDecimal? = null
    ) = Trn(
        trnType = TrnType.BALANCE,
        asset = asset,
        quantity = qty,
        tradeAmount = qty,
        contribution = contribution,
        cashCurrency = SGD,
        tradeCurrency = SGD,
        tradeCashRate = BigDecimal.ONE,
        tradeBaseRate = BigDecimal.ONE,
        tradePortfolioRate = BigDecimal.ONE,
        portfolio = portfolio
    )

    @Test
    fun `second BALANCE without a contribution tracks the balance so gain stays zero`() {
        // A bare BALANCE snapshot carries no contribution info. The increase
        // could be all contribution or all interest — unknowable — so cost
        // tracks the balance and no phantom gain is reported. (Regression:
        // pinning cost at the first snapshot turned every later contribution
        // into "gain", producing absurd growth like +3340%.)
        val portfolio = Portfolio(Constants.TEST, currency = SGD, base = SGD)
        val cpfAsset = cpfPolicyAsset()
        val secondBalance = BigDecimal("260000.00")
        val positions = Positions(portfolio)

        accumulator.accumulate(balanceTrn(portfolio, cpfAsset, BigDecimal("250000.00")), positions)
        val position = accumulator.accumulate(balanceTrn(portfolio, cpfAsset, secondBalance), positions)

        assertThat(position.quantityValues.purchased).isEqualByComparingTo(secondBalance)
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO))
            .hasFieldOrPropertyWithValue(PROP_COST_BASIS, secondBalance)
            .hasFieldOrPropertyWithValue(PROP_COST_VALUE, secondBalance)
        assertThat(position.getMoneyValues(Position.In.BASE))
            .hasFieldOrPropertyWithValue(PROP_COST_BASIS, secondBalance)
            .hasFieldOrPropertyWithValue(PROP_COST_VALUE, secondBalance)
    }

    @Test
    fun `second BALANCE grows cost by the contribution so only interest is gain`() {
        // First snapshot pins the starting principal (250,000). The second
        // snapshot is 260,000 with a recognised contribution of 8,000, so
        // cost = 250,000 + 8,000 = 258,000 and unrealised gain = 260,000 -
        // 258,000 = 2,000 (the interest), NOT the full 10,000 delta.
        val portfolio = Portfolio(Constants.TEST, currency = SGD, base = SGD)
        val cpfAsset = cpfPolicyAsset()
        val positions = Positions(portfolio)
        val expectedCost = BigDecimal("258000.00")

        accumulator.accumulate(balanceTrn(portfolio, cpfAsset, BigDecimal("250000.00")), positions)
        val position =
            accumulator.accumulate(
                balanceTrn(portfolio, cpfAsset, BigDecimal("260000.00"), contribution = BigDecimal("8000.00")),
                positions
            )

        assertThat(position.getMoneyValues(Position.In.PORTFOLIO))
            .hasFieldOrPropertyWithValue(PROP_COST_BASIS, expectedCost)
            .hasFieldOrPropertyWithValue(PROP_COST_VALUE, expectedCost)
        assertThat(position.getMoneyValues(Position.In.BASE))
            .hasFieldOrPropertyWithValue(PROP_COST_BASIS, expectedCost)
            .hasFieldOrPropertyWithValue(PROP_COST_VALUE, expectedCost)
    }

    @Test
    fun `an over-stated contribution is capped at market value so no phantom loss appears`() {
        // If the contribution estimate exceeds the actual balance increase,
        // cost is clamped to market value (gain 0) rather than showing a loss.
        val portfolio = Portfolio(Constants.TEST, currency = SGD, base = SGD)
        val cpfAsset = cpfPolicyAsset()
        val secondBalance = BigDecimal("255000.00")
        val positions = Positions(portfolio)

        accumulator.accumulate(balanceTrn(portfolio, cpfAsset, BigDecimal("250000.00")), positions)
        val position =
            accumulator.accumulate(
                // 9,000 contribution > 5,000 actual increase → cap at MV.
                balanceTrn(portfolio, cpfAsset, secondBalance, contribution = BigDecimal("9000.00")),
                positions
            )

        assertThat(position.getMoneyValues(Position.In.PORTFOLIO))
            .hasFieldOrPropertyWithValue(PROP_COST_BASIS, secondBalance)
            .hasFieldOrPropertyWithValue(PROP_COST_VALUE, secondBalance)
    }

    @Test
    fun `BALANCE with subAccounts captures per-bucket snapshot on position`() {
        // Composite-policy BALANCE (CPF, ILP, generic pension) carries a
        // sub-account map alongside the total. The accumulator must surface
        // it on Position.subAccounts so the holdings UI can render OA / SA /
        // MA / RA breakdown alongside the rolled-up MV.
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
        val subAccounts =
            mapOf(
                "OA" to BigDecimal("145000"),
                "SA" to BigDecimal("78000"),
                "MA" to BigDecimal("58000"),
                "RA" to BigDecimal("0")
            )
        val trn =
            Trn(
                trnType = TrnType.BALANCE,
                asset = cpfAsset,
                quantity = BigDecimal("281000"),
                tradeAmount = BigDecimal("281000"),
                cashCurrency = SGD,
                tradeCurrency = SGD,
                tradeCashRate = BigDecimal.ONE,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                portfolio = portfolio,
                subAccounts = subAccounts
            )

        val position = accumulator.accumulate(trn, Positions(portfolio))

        assertThat(position.subAccounts).containsExactlyInAnyOrderEntriesOf(subAccounts)
    }
}