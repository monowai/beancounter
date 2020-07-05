package com.beancounter.marketdata.providers.fxrates

import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory

class EcbDate {
    fun getValidDate(inDate: String?): String? {
        if (dateUtils.isToday(inDate)) {
            return dateUtils.today()
        }
        val requestedDate = dateUtils.getOrThrow(inDate)
        return if (requestedDate.isBefore(earliestDate)) {
            earliest
        } else inDate
    }

    companion object {
        const val earliest = "1999-01-04"
        private val dateUtils = DateUtils()
        private val earliestDate = dateUtils.getOrThrow(earliest)
        private val log = LoggerFactory.getLogger(EcbDate::class.java)
    }
}