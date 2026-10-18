package com.beancounter.marketdata.classification

import com.beancounter.common.contracts.BackfillResult
import com.beancounter.common.utils.DateUtils
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * The daily classification jobs: ETF sector exposures, and equity classifications, which also
 * snapshot SEC fundamentals for US filers.
 */
class ClassificationScheduleTest {
    private val refreshService =
        mock<ClassificationRefreshService> {
            on { refreshEtfSectors() } doReturn BackfillResult()
            on { refreshEquityClassifications() } doReturn BackfillResult()
        }
    private val schedule = ClassificationSchedule(refreshService, DateUtils())

    @Test
    fun `should refresh equity classifications on schedule`() {
        schedule.refreshEquityClassifications()

        verify(refreshService).refreshEquityClassifications()
    }
}