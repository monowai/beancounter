package com.beancounter.position.accumulation

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.hundred
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for SellBehaviour, particularly position closing logic.
 */
class SellBehaviourTest {
    private val buyBehaviour = BuyBehaviour()
    private val sellBehaviour = SellBehaviour()
    private val asset =
        Asset(
            "TEST",
            market = Constants.US
        )
    private val fifty = BigDecimal("50.0")

    @Test
    fun `closed date is set when position quantity reaches zero`() {
        val positions = Positions()
        val buyDate = LocalDate.of(2024, 1, 15)
        val sellDate = LocalDate.of(2024, 6, 20)

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

        // Verify position is open
        assertThat(position.quantityValues.getTotal()).isEqualTo(fifty)
        assertThat(position.dateValues.closed).isNull()

        // When I sell all 50 shares
        val sellTrn =
            Trn(
                asset = asset,
                trnType = TrnType.SELL,
                quantity = fifty,
                tradeAmount = BigDecimal("200"),
                cashAmount = BigDecimal("200"),
                tradeDate = sellDate
            )
        sellBehaviour.accumulate(
            sellTrn,
            positions,
            position
        )

        // Then position quantity is zero and closed date is set
        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(position.dateValues.closed).isEqualTo(sellDate)
    }

    @Test
    fun `closed date is not set when position is partially sold`() {
        val positions = Positions()
        val buyDate = LocalDate.of(2024, 1, 15)
        val sellDate = LocalDate.of(2024, 6, 20)

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

        // When I sell only 25 shares
        val sellTrn =
            Trn(
                asset = asset,
                trnType = TrnType.SELL,
                quantity = BigDecimal("25"),
                tradeAmount = hundred,
                cashAmount = hundred,
                tradeDate = sellDate
            )
        sellBehaviour.accumulate(
            sellTrn,
            positions,
            position
        )

        // Then position still has quantity and closed date is NOT set
        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal("25"))
        assertThat(position.dateValues.closed).isNull()
    }

    @Test
    fun `closed date is cleared when position is reopened`() {
        val positions = Positions()
        val buyDate1 = LocalDate.of(2024, 1, 15)
        val sellDate = LocalDate.of(2024, 6, 20)
        val buyDate2 = LocalDate.of(2024, 9, 10)

        // Given I buy 50 shares
        val buyTrn1 =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = fifty,
                tradeAmount = hundred,
                cashAmount = hundred.negate(),
                tradeDate = buyDate1
            )
        val position =
            buyBehaviour.accumulate(
                buyTrn1,
                positions
            )

        // And I sell all 50 shares (position closed)
        val sellTrn =
            Trn(
                asset = asset,
                trnType = TrnType.SELL,
                quantity = fifty,
                tradeAmount = BigDecimal("200"),
                cashAmount = BigDecimal("200"),
                tradeDate = sellDate
            )
        sellBehaviour.accumulate(
            sellTrn,
            positions,
            position
        )
        assertThat(position.dateValues.closed).isEqualTo(sellDate)

        // When I buy shares again (reopening position)
        val buyTrn2 =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = BigDecimal("30"),
                tradeAmount = BigDecimal("60"),
                cashAmount = BigDecimal("-60"),
                tradeDate = buyDate2
            )
        buyBehaviour.accumulate(
            buyTrn2,
            positions,
            position
        )

        // Then closed date should be cleared
        assertThat(position.quantityValues.getTotal()).isEqualByComparingTo(BigDecimal("30"))
        assertThat(position.dateValues.closed).isNull()
    }
}