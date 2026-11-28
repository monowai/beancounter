package com.beancounter.position.accumulation

import com.beancounter.common.contracts.PositionRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.TEST
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.holdings.PositionService
import com.beancounter.position.performance.HistoricalPrices
import com.beancounter.position.performance.SnapshotReplayer
import com.beancounter.position.performance.TwrBoundary
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A trn has a trade date and no time of day. Trns of one trade date accumulate buys and adds
 * first, then the split, then the rest, and a balance last, however they were stored.
 */
@SpringBootTest(classes = [Accumulator::class, PositionService::class])
class SameDayOrderTest {
    @Autowired
    private lateinit var positionService: PositionService

    @Autowired
    private lateinit var accumulator: Accumulator

    private val asset = Asset("TEST", market = Constants.US)
    private val portfolio = Portfolio(TEST, currency = USD, base = USD)
    private val day = LocalDate.of(2024, 3, 4)

    private fun trn(
        trnType: TrnType,
        quantity: String,
        tradeDate: LocalDate = day,
        of: Asset = asset
    ) = Trn(
        asset = of,
        trnType = trnType,
        quantity = BigDecimal(quantity),
        tradeAmount = BigDecimal(quantity),
        tradeDate = tradeDate,
        tradeCurrency = USD,
        portfolio = portfolio
    )

    @Test
    fun `should accumulate a sale stored before the same day's purchase`() {
        val sell = trn(TrnType.SELL, "10")
        val buy = trn(TrnType.BUY, "10")

        val positions = positionService.build(portfolio, PositionRequest(portfolio.id, listOf(sell, buy))).data

        assertThat(positions.getOrCreate(asset).quantityValues.getTotal()).isEqualByComparingTo("0")
    }

    @Test
    fun `should replay performance over a sale stored before the same day's purchase`() {
        val boundary = TwrBoundary()
        val stored = listOf(trn(TrnType.SELL, "10"), trn(TrnType.BUY, "10"))

        val snapshots =
            SnapshotReplayer(accumulator, boundary)
                .replay(portfolio, stored, listOf(day), HistoricalPrices(emptyMap(), emptyMap(), boundary))

        assertThat(snapshots.single().marketValue).isEqualByComparingTo("0")
    }

    @Test
    fun `should split the day's purchases before the day's sales`() {
        val sell = trn(TrnType.SELL, "20")
        val split = trn(TrnType.SPLIT, "2")
        val buy = trn(TrnType.BUY, "10")

        val positions = positionService.build(portfolio, PositionRequest(portfolio.id, listOf(sell, split, buy))).data

        assertThat(positions.getOrCreate(asset).quantityValues.getTotal()).isEqualByComparingTo("0")
    }

    @Test
    fun `should order one day as buys and adds, split, the rest, then balance`() {
        val stored =
            listOf(TrnType.BALANCE, TrnType.SELL, TrnType.DIVI, TrnType.SPLIT, TrnType.ADD, TrnType.REDUCE, TrnType.BUY)
                .map { trn(it, "1") }

        assertThat(AccumulationOrder.of(stored).map { it.trnType }).containsExactly(
            TrnType.ADD,
            TrnType.BUY,
            TrnType.SPLIT,
            TrnType.SELL,
            TrnType.DIVI,
            TrnType.REDUCE,
            TrnType.BALANCE
        )
    }

    @Test
    fun `should leave trade dates in order and never move a sale before an earlier day's trn`() {
        val earlierSell = trn(TrnType.SELL, "1", day.minusDays(1))
        val buy = trn(TrnType.BUY, "1")
        val laterBuy = trn(TrnType.BUY, "1", day.plusDays(1))
        val stored = listOf(earlierSell, buy, laterBuy)

        assertThat(AccumulationOrder.of(stored)).containsExactlyElementsOf(stored)
    }
}