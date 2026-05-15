package com.beancounter.marketdata.providers.eodhd.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDate

/**
 * EODHD dividend row from `/api/div/{symbol}`.
 *
 * Unlike AlphaVantage's TIME_SERIES_DAILY_ADJUSTED — which only exposes the ex-date — EODHD ships
 * declaration / record / payment dates separately. This lets us populate [com.beancounter.common.event.CorporateEvent.payDate]
 * accurately instead of synthesising `recordDate + 18 days` the way [com.beancounter.event.service.alpha.AlphaEventAdapter] does.
 */
data class EodhdDividend(
    val date: LocalDate,
    val declarationDate: LocalDate? = null,
    val recordDate: LocalDate? = null,
    val paymentDate: LocalDate? = null,
    val period: String? = null,
    val value: BigDecimal = BigDecimal.ZERO,
    @param:JsonProperty("unadjustedValue")
    val unadjustedValue: BigDecimal? = null,
    val currency: String? = null
)