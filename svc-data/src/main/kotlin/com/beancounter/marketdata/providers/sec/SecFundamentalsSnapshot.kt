package com.beancounter.marketdata.providers.sec

import java.math.BigDecimal
import java.time.LocalDate

/**
 * The fiscal-year measures lifted out of an SEC `companyfacts` document by
 * [SecFundamentalsParser]. Any measure the filer did not report is null.
 */
data class SecFundamentalsSnapshot(
    val fiscalYearEnd: LocalDate,
    val fiscalYear: Int,
    val epsDiluted: BigDecimal?,
    val revenue: BigDecimal?,
    val netIncome: BigDecimal?,
    val dividendsPerShare: BigDecimal?,
    val sharesOutstanding: Long?
)