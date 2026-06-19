package com.beancounter.common.contracts

import com.beancounter.common.model.Currency
import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDate

data class PerformanceDataPoint(
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val date: LocalDate,
    val growthOf1000: BigDecimal = BigDecimal.ZERO,
    val marketValue: BigDecimal = BigDecimal.ZERO,
    val netContributions: BigDecimal = BigDecimal.ZERO,
    val cumulativeReturn: BigDecimal = BigDecimal.ZERO,
    val cumulativeDividends: BigDecimal = BigDecimal.ZERO
)

data class PerformanceData(
    val currency: Currency,
    val series: List<PerformanceDataPoint> = emptyList(),
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val firstTradeDate: LocalDate? = null
)

data class PerformanceResponse(
    override val data: PerformanceData
) : Payload<PerformanceData>