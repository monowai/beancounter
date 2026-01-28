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
import com.beancounter.position.Constants.Companion.usdCashBalance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

/**
 * Tests for ExpenseBehaviour - verifies expense transactions for real estate/private assets.
 * Expenses should:
 * 1. Track expense amount against the asset's MoneyValues.expenses
 * 2. Debit cash from the selected cash account
 * 3. NOT affect cost basis or position quantity
 */
@SpringBootTest(classes = [Accumulator::class])
class ExpenseBehaviourTest {
    @Autowired
    private lateinit var accumulator: Accumulator

    private val expenseAmount = BigDecimal("1500.00")

    private fun createRealEstateAsset(): Asset =
        Asset.of(
            AssetInput.toRealEstate(
                USD,
                "USAPT",
                "NY Apartment",
                "test-user"
            ),
            market = Market("RE")
        )

    @Test
    fun `should track expense against asset`() {
        val portfolio = Portfolio(TEST, currency = USD, base = USD)
        val realEstate = createRealEstateAsset()

        val trn =
            Trn(
                trnType = TrnType.EXPENSE,
                asset = realEstate,
                quantity = BigDecimal.ZERO, // Expense doesn't change quantity
                tradeAmount = expenseAmount,
                tradeCurrency = USD,
                cashAsset = usdCashBalance,
                cashCurrency = USD,
                cashAmount = expenseAmount,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                portfolio = portfolio
            )

        val positions = Positions()
        val position = accumulator.accumulate(trn, positions)

        // Verify expense is tracked in MoneyValues
        assertThat(
            position.getMoneyValues(Position.In.TRADE, USD)
        ).hasFieldOrPropertyWithValue("expenses", expenseAmount)
    }

    @Test
    fun `should debit cash from selected account`() {
        val portfolio = Portfolio(TEST, currency = USD, base = USD)
        val realEstate = createRealEstateAsset()

        val trn =
            Trn(
                trnType = TrnType.EXPENSE,
                asset = realEstate,
                quantity = BigDecimal.ZERO,
                tradeAmount = expenseAmount,
                tradeCurrency = USD,
                cashAsset = usdCashBalance,
                cashCurrency = USD,
                cashAmount = expenseAmount,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                portfolio = portfolio
            )

        val positions = Positions()
        accumulator.accumulate(trn, positions)

        // Verify cash position was created and debited
        assertThat(positions.positions).hasSize(2) // Real estate + cash
        val cashPosition = positions.getOrCreate(usdCashBalance)
        assertThat(cashPosition.quantityValues)
            .hasFieldOrPropertyWithValue("sold", expenseAmount.negate())
    }

    @Test
    fun `should not affect cost basis or quantity`() {
        val portfolio = Portfolio(TEST, currency = USD, base = USD)
        val realEstate = createRealEstateAsset()

        // First, add the real estate with an initial cost
        val initialCost = BigDecimal("500000.00")
        val addTrn =
            Trn(
                trnType = TrnType.ADD,
                asset = realEstate,
                quantity = BigDecimal.ONE,
                price = initialCost,
                tradeAmount = initialCost,
                tradeCurrency = USD,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                portfolio = portfolio
            )

        val positions = Positions()
        val positionAfterAdd = accumulator.accumulate(addTrn, positions)

        val costBasisBeforeExpense = positionAfterAdd.getMoneyValues(Position.In.TRADE, USD).costBasis
        val quantityBeforeExpense = positionAfterAdd.quantityValues.getTotal()

        // Now add an expense
        val expenseTrn =
            Trn(
                trnType = TrnType.EXPENSE,
                asset = realEstate,
                quantity = BigDecimal.ZERO,
                tradeAmount = expenseAmount,
                tradeCurrency = USD,
                cashAsset = usdCashBalance,
                cashCurrency = USD,
                cashAmount = expenseAmount,
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                portfolio = portfolio
            )

        val positionAfterExpense = accumulator.accumulate(expenseTrn, positions)

        // Verify cost basis is unchanged
        assertThat(positionAfterExpense.getMoneyValues(Position.In.TRADE, USD).costBasis)
            .isEqualTo(costBasisBeforeExpense)

        // Verify quantity is unchanged
        assertThat(positionAfterExpense.quantityValues.getTotal())
            .isEqualByComparingTo(quantityBeforeExpense)

        // Verify expense is tracked
        assertThat(positionAfterExpense.getMoneyValues(Position.In.TRADE, USD).expenses)
            .isEqualTo(expenseAmount)
    }

    @Test
    fun `should track expenses across multiple currencies`() {
        val portfolio = Portfolio(TEST, currency = USD, base = NZD)
        val realEstate = createRealEstateAsset()

        val trn =
            Trn(
                trnType = TrnType.EXPENSE,
                asset = realEstate,
                quantity = BigDecimal.ZERO,
                tradeAmount = expenseAmount,
                tradeCurrency = USD,
                cashAsset = usdCashBalance,
                cashCurrency = USD,
                cashAmount = expenseAmount,
                tradeBaseRate = BigDecimal("1.50"), // USD to NZD
                tradePortfolioRate = BigDecimal.ONE, // USD to USD
                tradeCashRate = BigDecimal.ONE,
                portfolio = portfolio
            )

        val positions = Positions()
        val position = accumulator.accumulate(trn, positions)

        // Verify expense in trade currency (USD)
        assertThat(position.getMoneyValues(Position.In.TRADE, USD).expenses)
            .isEqualTo(expenseAmount)

        // Verify expense in base currency (NZD) - converted at rate 1.50
        assertThat(position.getMoneyValues(Position.In.BASE, NZD).expenses)
            .isEqualByComparingTo(expenseAmount.multiply(BigDecimal("1.50")))

        // Verify expense in portfolio currency (USD)
        assertThat(position.getMoneyValues(Position.In.PORTFOLIO, USD).expenses)
            .isEqualTo(expenseAmount)
    }
}