package com.beancounter.position.schedule

import com.beancounter.common.utils.DateUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

/**
 * Cron now delegates the actual loop to [PortfolioRevaluator]; this test
 * exists to lock in the contract that the scheduled task fires with
 * `reason=cron`. The loop body itself is covered by
 * [PortfolioRevaluatorTest].
 */
@ExtendWith(MockitoExtension::class)
class PortfolioValuationScheduleTest {
    @Mock
    private lateinit var revaluator: PortfolioRevaluator

    @Test
    fun `cron delegates to revaluator with reason cron`() {
        val schedule =
            PortfolioValuationSchedule(
                revaluator = revaluator,
                dateUtils = DateUtils(),
                schedule = "0 0 7 * * *"
            )
        schedule.valuePortfolios()
        verify(revaluator).revalueAll(eq("cron"))
    }
}