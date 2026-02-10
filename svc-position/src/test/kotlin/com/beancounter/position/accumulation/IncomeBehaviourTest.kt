package com.beancounter.position.accumulation

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.TEST
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.nzdCashBalance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

/**
 * Tests for IncomeBehaviour - verifies income transactions (interest, salary, etc.)
 * accumulate into the dividends (income) column on the asset position.
 * Income should:
 * 1. Track income amount in MoneyValues.dividends (the "Income" column)
 * 2. Credit cash to the selected cash account
 * 3. NOT affect cost basis, market value, or position quantity
 */
@SpringBootTest(classes = [Accumulator::class])
class IncomeBehaviourTest {
    @Autowired
    private lateinit var accumulator: Accumulator

    private val incomeAmount = BigDecimal("250.00")

    private fun createRealEstateAsset(): Asset =
        Asset.of(
            AssetInput.toRealEstate(
                NZD,
                "NZAPT",
                "Auckland Apartment",
                "test-user"
            ),
            market = Market("RE")
        )

    @Test
    fun `should track income in dividends column`() {
        val portfolio = Portfolio(TEST, currency = NZD, base = NZD)
        val realEstate = createRealEstateAsset()

        // TrnInputMapper converts quantity=0 to quantity=tradeAmount for isCash types
        val trn =
            Trn(
                trnType = TrnType.INCOME,
                asset = realEstate,
                quantity = incomeAmount,
                tradeAmount = incomeAmount,
                tradeCurrency = NZD,
                cashAsset = nzdCashBalance,
                cashCurrency = NZD,
                cashAmount = incomeAmount,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                portfolio = portfolio
            )

        val positions = Positions()
        val position = accumulator.accumulate(trn, positions)

        // Income should accumulate into the dividends (income) column
        assertThat(
            position.getMoneyValues(Position.In.TRADE, NZD)
        ).hasFieldOrPropertyWithValue("dividends", incomeAmount)
    }

    @Test
    fun `should credit cash to selected account`() {
        val portfolio = Portfolio(TEST, currency = NZD, base = NZD)
        val realEstate = createRealEstateAsset()

        val trn =
            Trn(
                trnType = TrnType.INCOME,
                asset = realEstate,
                quantity = incomeAmount, // Mapper sets quantity=tradeAmount for isCash types
                tradeAmount = incomeAmount,
                tradeCurrency = NZD,
                cashAsset = nzdCashBalance,
                cashCurrency = NZD,
                cashAmount = incomeAmount,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                portfolio = portfolio
            )

        val positions = Positions()
        accumulator.accumulate(trn, positions)

        // Verify cash position was created and credited
        assertThat(positions.positions).hasSize(2) // Asset + cash
        val cashPosition = positions.getOrCreate(nzdCashBalance)
        assertThat(cashPosition.quantityValues)
            .hasFieldOrPropertyWithValue("purchased", incomeAmount)
    }

    @Test
    fun `should not affect cost basis or quantity`() {
        val portfolio = Portfolio(TEST, currency = NZD, base = NZD)
        val realEstate = createRealEstateAsset()

        // First, add the real estate with an initial cost
        val initialCost = BigDecimal("750000.00")
        val addTrn =
            Trn(
                trnType = TrnType.ADD,
                asset = realEstate,
                quantity = BigDecimal.ONE,
                price = initialCost,
                tradeAmount = initialCost,
                tradeCurrency = NZD,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                portfolio = portfolio
            )

        val positions = Positions()
        val positionAfterAdd = accumulator.accumulate(addTrn, positions)

        val costBasisBefore = positionAfterAdd.getMoneyValues(Position.In.TRADE, NZD).costBasis
        val costValueBefore = positionAfterAdd.getMoneyValues(Position.In.TRADE, NZD).costValue
        val quantityBefore = positionAfterAdd.quantityValues.getTotal()

        // Now add income
        val incomeTrn =
            Trn(
                trnType = TrnType.INCOME,
                asset = realEstate,
                quantity = incomeAmount, // Mapper sets quantity=tradeAmount for isCash types
                tradeAmount = incomeAmount,
                tradeCurrency = NZD,
                cashAsset = nzdCashBalance,
                cashCurrency = NZD,
                cashAmount = incomeAmount,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                portfolio = portfolio
            )

        val positionAfterIncome = accumulator.accumulate(incomeTrn, positions)

        // Verify cost basis is unchanged
        assertThat(positionAfterIncome.getMoneyValues(Position.In.TRADE, NZD).costBasis)
            .isEqualTo(costBasisBefore)

        // Verify cost value is unchanged
        assertThat(positionAfterIncome.getMoneyValues(Position.In.TRADE, NZD).costValue)
            .isEqualTo(costValueBefore)

        // Verify quantity is unchanged
        assertThat(positionAfterIncome.quantityValues.getTotal())
            .isEqualByComparingTo(quantityBefore)

        // Verify income is tracked in dividends
        assertThat(positionAfterIncome.getMoneyValues(Position.In.TRADE, NZD).dividends)
            .isEqualTo(incomeAmount)
    }

    @Test
    fun `should track income across multiple currencies`() {
        val portfolio = Portfolio(TEST, currency = NZD, base = USD)
        val realEstate = createRealEstateAsset()

        val trn =
            Trn(
                trnType = TrnType.INCOME,
                asset = realEstate,
                quantity = incomeAmount, // Mapper sets quantity=tradeAmount for isCash types
                tradeAmount = incomeAmount,
                tradeCurrency = NZD,
                cashAsset = nzdCashBalance,
                cashCurrency = NZD,
                cashAmount = incomeAmount,
                tradeBaseRate = BigDecimal("0.62"), // NZD to USD
                tradePortfolioRate = BigDecimal.ONE, // NZD to NZD
                tradeCashRate = BigDecimal.ONE,
                portfolio = portfolio
            )

        val positions = Positions()
        val position = accumulator.accumulate(trn, positions)

        // Verify income in trade currency (NZD)
        assertThat(position.getMoneyValues(Position.In.TRADE, NZD).dividends)
            .isEqualTo(incomeAmount)

        // Verify income in base currency (USD) - converted at rate 0.62
        assertThat(position.getMoneyValues(Position.In.BASE, USD).dividends)
            .isEqualByComparingTo(incomeAmount.multiply(BigDecimal("0.62")))

        // Verify income in portfolio currency (NZD)
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, NZD).dividends)
            .isEqualTo(incomeAmount)
    }
}