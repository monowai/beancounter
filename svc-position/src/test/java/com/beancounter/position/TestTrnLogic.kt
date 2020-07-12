package com.beancounter.position

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.service.Accumulator
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Test rules that would prevent a transaction from accumulating.
 */
@SpringBootTest(classes = [Accumulator::class])
internal class TestTrnLogic {
    @Autowired
    private lateinit var accumulator: Accumulator

    /**
     * Transactions should be ordered.  If the date is ==, then it will be accepted but
     * unordered transactions will result in an Exception being thrown
     */
    @Test
    fun do_UnorderedTransactionsError() {
        val apple = getAsset("NASDAQ", "AAPL")
        val positions = Positions(getPortfolio("TEST"))
        var position = positions[apple]
        assertThat(position).isNotNull
        val today = LocalDate.now()
        val yesterday = today.minus(-1, ChronoUnit.DAYS)
        val buyYesterday = Trn(TrnType.BUY, apple, BigDecimal(100), tradeDate = DateUtils().convert(yesterday)!!)
        buyYesterday.tradeAmount = BigDecimal(2000)
        val buyToday = Trn(TrnType.BUY, apple, BigDecimal(100), tradeDate = DateUtils().convert(today)!!)
        buyToday.tradeAmount = BigDecimal(2000)
        positions.add(position)
        position = accumulator.accumulate(buyYesterday, positions.portfolio, position)
        val finalPosition = position
        assertThrows(BusinessException::class.java) {
            accumulator.accumulate(buyToday, positions.portfolio, finalPosition)
        }
    }
}