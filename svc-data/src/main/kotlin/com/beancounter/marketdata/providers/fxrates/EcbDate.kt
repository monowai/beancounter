package com.beancounter.marketdata.providers.fxrates

import com.beancounter.common.utils.DateUtils
import java.time.LocalDate

/**
 * Simple helper to assist with the ECB FX Rate provider needs.
 *
 * Today is treated as T-1
 */
class EcbDate(val dateUtils: DateUtils) {
    private fun earliestDate(): LocalDate {
        return dateUtils.getOrThrow(earliest)
    }

    fun getValidDate(inDate: String): String {
        if (dateUtils.isToday(inDate)) {
            return dateUtils.getDateString(dateUtils.getDate(inDate).minusDays(1))
        }
        val requestedDate = dateUtils.getOrThrow(inDate)
        return if (requestedDate.isBefore(earliestDate())) {
            earliest
        } else inDate
    }

    companion object {
        const val earliest = "1999-01-04"
    }
}
