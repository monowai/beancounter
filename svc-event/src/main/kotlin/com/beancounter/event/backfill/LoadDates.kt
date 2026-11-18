package com.beancounter.event.backfill

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.DateUtils

/** Loading runs from the date up to today, so a later date has nothing to load. */
internal fun DateUtils.requireNotAfterToday(
    name: String,
    date: String
) {
    if (getDate(date).isAfter(this.date)) {
        throw BusinessException("$name $date is after today; events load from a date up to today")
    }
}