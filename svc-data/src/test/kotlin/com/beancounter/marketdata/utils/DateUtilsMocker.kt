package com.beancounter.marketdata.utils

import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.today
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

/**
 * Helper to mock out the widely used DateUtils class.
 */
class DateUtilsMocker {
    companion object {
        @JvmStatic
        fun mockToday(dateUtils: DateUtils) {
            Mockito.`when`(dateUtils.isToday(ArgumentMatchers.anyString()))
                .thenReturn(true)

            Mockito.`when`(dateUtils.offset(ArgumentMatchers.anyString()))
                .thenReturn(DateUtils().offset())

            Mockito.`when`(dateUtils.offsetDateString(ArgumentMatchers.anyString()))
                .thenReturn(DateUtils().offsetDateString())

            var i = 0L

            while (i < 5) {
                val yesterday = DateUtils().getDate().minusDays(i).toString()
                Mockito.`when`(dateUtils.getDate(yesterday))
                    .thenReturn(DateUtils().getDate().minusDays(i))
                i++
            }

            Mockito.`when`(dateUtils.getDate(today))
                .thenReturn(DateUtils().getDate())
        }
    }
}
