package com.beancounter.event.common

import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * verifies various date splitting scenarios.
 */
internal class DateSplitterTest {
    private val dateUtils = DateUtils()
    private val dateSplitter = DateSplitter(dateUtils)

    @Test
    fun splitsFromToEveryOneDay() {
        // Calculate all dates - FromDate, every 1 day up until 20th
        val results =
            dateSplitter.split(
                1,
                "2022-01-01",
                "2022-01-20",
            )
        assertThat(results)
            .size()
            .isEqualTo(20)
    }

    @Test
    fun splitsFromToEveryTwoDays() {
        // Calculate all the dates - FromDate, every 1 day up until 20th
        val results =
            dateSplitter.split(
                2,
                "2022-01-01",
                "2022-01-21",
            )
        assertThat(results)
            .size()
            .isEqualTo(11) // Always has the last date
    }

    @Test
    fun splitsTodayOnly() {
        // Calculate all the dates - FromDate, every 1 day up until 20th
        val results =
            dateSplitter.split(
                20,
                dateUtils.today(),
                dateUtils.today(),
            )
        assertThat(results)
            .size()
            .isEqualTo(1)
    }

    @Test
    fun splitsDefaults() {
        // T-25 and the end date
        val results = dateSplitter.split()
        assertThat(results)
            .size()
            .isEqualTo(2)
    }
}
