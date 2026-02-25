package com.beancounter.position.accumulation

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants.Companion.SGD
import com.beancounter.position.Constants.Companion.TEST
import com.beancounter.position.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

/**
 * Verifies that interest (INCOME) on a private SGD cash account settles
 * only to that account — no generic "SGD Balance" position should be created.
 */
@SpringBootTest(classes = [Accumulator::class])
class PrivateCashIncomeBehaviourTest {
    @Autowired
    private lateinit var accumulator: Accumulator

    private val interestAmount = BigDecimal("150.00")

    private fun createPrivateCashAsset(): Asset =
        Asset.of(
            AssetInput.toAccount(
                SGD,
                "SGD-SAVINGS",
                "SGD Savings Account",
                "test-user"
            ),
            market = Market("PRIVATE")
        )

    @Test
    fun `interest on private cash account should not create generic SGD balance position`() {
        val portfolio = Portfolio(TEST, currency = SGD, base = USD)
        val privateCash = createPrivateCashAsset()

        val trn =
            Trn(
                trnType = TrnType.INCOME,
                asset = privateCash,
                quantity = BigDecimal.ONE,
                tradeAmount = interestAmount,
                tradeCurrency = SGD,
                cashAsset = privateCash,
                cashCurrency = SGD,
                cashAmount = interestAmount,
                tradeBaseRate = BigDecimal("0.74"),
                tradePortfolioRate = BigDecimal.ONE,
                tradeCashRate = BigDecimal.ONE,
                portfolio = portfolio
            )

        val positions = Positions()
        val position = accumulator.accumulate(trn, positions)

        // Only the private cash position should exist — no generic SGD Balance
        assertThat(positions.positions).hasSize(1)

        // Interest tracked in dividends column
        assertThat(position.getMoneyValues(Position.In.TRADE, SGD))
            .hasFieldOrPropertyWithValue("dividends", interestAmount)

        // Cash credited to the same private account
        assertThat(position.quantityValues)
            .hasFieldOrPropertyWithValue("purchased", interestAmount)

        // No "SGD BALANCE" position was created
        assertThat(
            positions.positions.keys.none { it.contains("BALANCE") }
        ).isTrue()
    }
}