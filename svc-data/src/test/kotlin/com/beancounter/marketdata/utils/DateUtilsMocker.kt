package com.beancounter.marketdata.utils

import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Helper to mock out the widely used DateUtils class.
 */
class DateUtilsMocker {
    companion object {
        @JvmStatic
        fun mockToday(dateUtils: DateUtils) {
            Mockito
                .`when`(dateUtils.isToday(anyString()))
                .thenReturn(true)

            Mockito
                .`when`(dateUtils.offsetDateString(anyString()))
                .thenReturn(DateUtils().offsetDateString())

            // Simplify the mocking of formatted date across a range of days
            repeat(5) { daysAgo ->
                val specificDay = LocalDate.now().minusDays(daysAgo.toLong())
                val specificDayString = specificDay.format(DateTimeFormatter.ISO_LOCAL_DATE)
                Mockito
                    .`when`(dateUtils.getDate(specificDayString))
                    .thenReturn(specificDay)

                Mockito
                    .`when`(dateUtils.getFormattedDate(specificDayString))
                    .thenReturn(specificDay)
            }

            Mockito
                .`when`(dateUtils.getDate())
                .thenReturn(DateUtils().getDate())

            Mockito
                .`when`(dateUtils.getFormattedDate(TODAY))
                .thenReturn(DateUtils().getFormattedDate())
        }
    }
}
