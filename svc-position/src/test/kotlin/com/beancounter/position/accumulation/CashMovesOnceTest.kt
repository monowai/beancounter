package com.beancounter.position.accumulation

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants.Companion.CASH
import com.beancounter.position.Constants.Companion.SGD
import com.beancounter.position.Constants.Companion.TEST
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate
import java.util.stream.Stream

/**
 * Each trn moves any one position's cash quantity once, whether the trn's cash asset is
 * the trn's own asset (payslip legs) or a separate cash asset (a charge on a property).
 */
@SpringBootTest(classes = [Accumulator::class])
class CashMovesOnceTest {
    @Autowired
    private lateinit var accumulator: Accumulator

    private val portfolio = Portfolio(TEST, currency = SGD, base = SGD)
    private val opening = BigDecimal("13855.06")
    private val amount = BigDecimal("250")
    private val tradeDate: LocalDate = LocalDate.of(2026, 11, 1)

    companion object {
        private val cashTypes =
            listOf(TrnType.DEDUCTION, TrnType.EXPENSE, TrnType.INCOME, TrnType.WITHDRAWAL, TrnType.DEPOSIT)

        private fun plainCash(): Asset =
            Asset(
                code = "SGD BALANCE",
                id = "SGD BALANCE",
                name = "SGD Balance",
                market = Market(CASH.code, currencyId = SGD.code),
                priceSymbol = SGD.code,
                category = CASH.code
            )

        private fun bankAccount(): Asset =
            Asset.of(
                AssetInput.toAccount(SGD, "SGD-SAVINGS", "SGD Savings Account", "test-user"),
                market = Market("PRIVATE")
            )

        // DEPOSIT and WITHDRAWAL are only valid against a cash asset.
        private val chargeTypes = listOf(TrnType.DEDUCTION, TrnType.EXPENSE, TrnType.INCOME)

        @JvmStatic
        fun cashAssets(): Stream<Arguments> = against(cashTypes)

        @JvmStatic
        fun chargesAgainstProperty(): Stream<Arguments> = against(chargeTypes)

        private fun against(types: List<TrnType>): Stream<Arguments> =
            types
                .flatMap { type ->
                    listOf(
                        Arguments.of(type, Named.of("CASH", plainCash())),
                        Arguments.of(type, Named.of("ACCOUNT", bankAccount()))
                    )
                }.stream()
    }

    private fun direction(type: TrnType): BigDecimal =
        if (TrnType.isCashCredited(type)) BigDecimal.ONE else BigDecimal.ONE.negate()

    private fun trn(
        type: TrnType,
        asset: Asset,
        cashAsset: Asset
    ) = Trn(
        trnType = type,
        asset = asset,
        quantity = amount,
        tradeAmount = amount,
        tradeCurrency = SGD,
        tradeDate = tradeDate,
        cashAsset = cashAsset,
        cashCurrency = SGD,
        cashAmount = amount.multiply(direction(type)),
        tradeBaseRate = BigDecimal.ONE,
        tradePortfolioRate = BigDecimal.ONE,
        tradeCashRate = BigDecimal.ONE,
        portfolio = portfolio
    )

    private fun openedWith(cashAsset: Asset): Positions {
        val positions = Positions(portfolio)
        accumulator.accumulate(
            Trn(
                trnType = TrnType.DEPOSIT,
                asset = cashAsset,
                quantity = opening,
                tradeAmount = opening,
                tradeCurrency = SGD,
                tradeDate = tradeDate,
                cashAsset = cashAsset,
                cashCurrency = SGD,
                cashAmount = opening,
                portfolio = portfolio
            ),
            positions
        )
        return positions
    }

    @ParameterizedTest(name = "{0} against {1} asset whose cash asset is itself")
    @MethodSource("cashAssets")
    fun `trn against a cash asset settling to itself moves its balance once`(
        type: TrnType,
        cashAsset: Asset
    ) {
        val positions = openedWith(cashAsset)

        val position = accumulator.accumulate(trn(type, cashAsset, cashAsset), positions)

        assertThat(position.quantityValues.getTotal())
            .isEqualByComparingTo(opening.add(amount.multiply(direction(type))))
        assertThat(positions.positions).hasSize(1)
    }

    @ParameterizedTest(name = "{0} against a property, settling to a separate {1} asset")
    @MethodSource("chargesAgainstProperty")
    fun `trn against a non-cash asset moves its separate cash asset once`(
        type: TrnType,
        cashAsset: Asset
    ) {
        val positions = openedWith(cashAsset)
        val property =
            Asset.of(
                AssetInput.toRealEstate(SGD, "SGAPT", "SG Apartment", "test-user"),
                market = Market("RE")
            )

        val position = accumulator.accumulate(trn(type, property, cashAsset), positions)

        assertThat(positions.getOrCreate(cashAsset).quantityValues.getTotal())
            .isEqualByComparingTo(opening.add(amount.multiply(direction(type))))
        // A charge on a property (maintenance, rates) moves only cash, never the property itself.
        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal.ZERO)
    }
}