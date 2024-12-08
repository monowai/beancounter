package com.beancounter.position.accumulation

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.position.Constants.Companion.AAPL
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.hundred
import com.beancounter.position.Constants.Companion.twoK
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Test rules that would prevent a transaction from accumulating.
 */
@SpringBootTest(classes = [Accumulator::class, DateUtils::class])
internal class TrnOrderTest {
    @Autowired
    private lateinit var accumulator: Accumulator

    @Autowired
    private lateinit var dateUtils: DateUtils

    /**
     * Transactions should be ordered.  If the date is ==, then it will be accepted but
     * unordered transactions will result in an Exception being thrown
     */
    @Test
    fun do_UnorderedTransactionsError() {
        val apple =
            getTestAsset(
                NASDAQ,
                AAPL,
            )
        val positions = Positions(getPortfolio())
        val today = LocalDate.now()
        val yesterday =
            today.minus(
                -1,
                ChronoUnit.DAYS,
            )
        val buyYesterday =
            Trn(
                trnType = TrnType.BUY,
                tradeDate =
                yesterday
                    .atStartOfDay(dateUtils.zoneId)
                    .toLocalDate(),
                asset = apple,
                quantity = hundred,
                tradeAmount = twoK,
            )
        accumulator.accumulate(
            buyYesterday,
            positions,
        )
        val buyToday =
            Trn(
                trnType = TrnType.BUY,
                tradeDate =
                today
                    .atStartOfDay(dateUtils.zoneId)
                    .toLocalDate(),
                asset = apple,
                quantity = hundred,
                tradeAmount = twoK,
            )

        assertThrows(BusinessException::class.java) {
            accumulator.accumulate(
                buyToday,
                positions,
            )
        }
    }
}
