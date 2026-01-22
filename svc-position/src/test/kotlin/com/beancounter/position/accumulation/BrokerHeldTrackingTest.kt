package com.beancounter.position.accumulation

import com.beancounter.common.model.Broker
import com.beancounter.common.model.Positions
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.position.Constants
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for broker-wise quantity tracking in positions.
 * Verifies that the `held` map on Position correctly tracks
 * quantities per broker and clears brokers when fully sold.
 */
class BrokerHeldTrackingTest {
    private val behaviours =
        listOf(
            BuyBehaviour(),
            SellBehaviour(),
            SplitBehaviour()
        )
    private val accumulator = Accumulator(TrnBehaviourFactory(behaviours))
    private val asset = AssetUtils.getTestAsset(Constants.US, "TEST")
    private val owner = SystemUser(id = "test-owner", email = "test@test.com")
    private val brokerA = Broker(id = "broker-a", name = "Broker A", owner = owner)
    private val brokerB = Broker(id = "broker-b", name = "Broker B", owner = owner)
    private val hundred = BigDecimal("100")
    private val fifty = BigDecimal("50")

    @Test
    fun `should track broker quantity on buy`() {
        val positions = Positions()
        val buyTrn =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = hundred,
                tradeAmount = BigDecimal("1000"),
                broker = brokerA,
                tradeDate = LocalDate.now()
            )

        val position = accumulator.accumulate(buyTrn, positions)

        assertThat(position.held).hasSize(1)
        assertThat(position.held["Broker A"]).isEqualByComparingTo(hundred)
    }

    @Test
    fun `should track multiple brokers separately`() {
        val positions = Positions()

        // Buy 100 via Broker A
        val buyTrnA =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = hundred,
                tradeAmount = BigDecimal("1000"),
                broker = brokerA,
                tradeDate = LocalDate.now()
            )
        accumulator.accumulate(buyTrnA, positions)

        // Buy 50 via Broker B
        val buyTrnB =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = fifty,
                tradeAmount = BigDecimal("500"),
                broker = brokerB,
                tradeDate = LocalDate.now()
            )
        val position = accumulator.accumulate(buyTrnB, positions)

        assertThat(position.held).hasSize(2)
        assertThat(position.held["Broker A"]).isEqualByComparingTo(hundred)
        assertThat(position.held["Broker B"]).isEqualByComparingTo(fifty)
    }

    @Test
    fun `should reduce broker quantity on sell`() {
        val positions = Positions()

        // Buy 100 via Broker A
        val buyTrn =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = hundred,
                tradeAmount = BigDecimal("1000"),
                broker = brokerA,
                tradeDate = LocalDate.now()
            )
        accumulator.accumulate(buyTrn, positions)

        // Sell 50 via Broker A
        val sellTrn =
            Trn(
                asset = asset,
                trnType = TrnType.SELL,
                quantity = fifty,
                tradeAmount = BigDecimal("500"),
                broker = brokerA,
                tradeDate = LocalDate.now()
            )
        val position = accumulator.accumulate(sellTrn, positions)

        assertThat(position.held).hasSize(1)
        assertThat(position.held["Broker A"]).isEqualByComparingTo(fifty)
    }

    @Test
    fun `should remove broker from held when quantity goes to zero`() {
        val positions = Positions()

        // Buy 100 via Broker A
        val buyTrn =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = hundred,
                tradeAmount = BigDecimal("1000"),
                broker = brokerA,
                tradeDate = LocalDate.now()
            )
        accumulator.accumulate(buyTrn, positions)

        // Sell all 100 via Broker A
        val sellTrn =
            Trn(
                asset = asset,
                trnType = TrnType.SELL,
                quantity = hundred,
                tradeAmount = BigDecimal("1000"),
                broker = brokerA,
                tradeDate = LocalDate.now()
            )
        val position = accumulator.accumulate(sellTrn, positions)

        assertThat(position.held).isEmpty()
        assertThat(position.held.containsKey("Broker A")).isFalse()
    }

    @Test
    fun `should remove only sold-out broker when multiple brokers exist`() {
        val positions = Positions()

        // Buy 100 via Broker A
        val buyTrnA =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = hundred,
                tradeAmount = BigDecimal("1000"),
                broker = brokerA,
                tradeDate = LocalDate.now()
            )
        accumulator.accumulate(buyTrnA, positions)

        // Buy 50 via Broker B
        val buyTrnB =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = fifty,
                tradeAmount = BigDecimal("500"),
                broker = brokerB,
                tradeDate = LocalDate.now()
            )
        accumulator.accumulate(buyTrnB, positions)

        // Sell all 100 via Broker A
        val sellTrn =
            Trn(
                asset = asset,
                trnType = TrnType.SELL,
                quantity = hundred,
                tradeAmount = BigDecimal("1000"),
                broker = brokerA,
                tradeDate = LocalDate.now()
            )
        val position = accumulator.accumulate(sellTrn, positions)

        // Broker A should be removed, Broker B should remain
        assertThat(position.held).hasSize(1)
        assertThat(position.held.containsKey("Broker A")).isFalse()
        assertThat(position.held["Broker B"]).isEqualByComparingTo(fifty)
    }

    @Test
    fun `should not track broker for transactions without broker`() {
        val positions = Positions()

        // Buy without broker
        val buyTrn =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = hundred,
                tradeAmount = BigDecimal("1000"),
                broker = null,
                tradeDate = LocalDate.now()
            )
        val position = accumulator.accumulate(buyTrn, positions)

        assertThat(position.held).isEmpty()
    }

    @Test
    fun `should not affect held on SPLIT transaction`() {
        val positions = Positions()

        // Buy 100 via Broker A
        val buyTrn =
            Trn(
                asset = asset,
                trnType = TrnType.BUY,
                quantity = hundred,
                tradeAmount = BigDecimal("1000"),
                broker = brokerA,
                tradeDate = LocalDate.now()
            )
        accumulator.accumulate(buyTrn, positions)

        // Split (2:1) via Broker A - should not affect held tracking
        val splitTrn =
            Trn(
                asset = asset,
                trnType = TrnType.SPLIT,
                quantity = hundred, // Additional shares from split
                tradeAmount = BigDecimal.ZERO,
                broker = brokerA,
                tradeDate = LocalDate.now()
            )
        val position = accumulator.accumulate(splitTrn, positions)

        // Held should still show original 100, not 200
        // (SPLIT affects total quantity but not broker breakdown per plan)
        assertThat(position.held["Broker A"]).isEqualByComparingTo(hundred)
    }
}