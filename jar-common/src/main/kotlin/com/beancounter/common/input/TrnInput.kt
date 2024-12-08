package com.beancounter.common.input

import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Incoming request to mutate a transaction
 */
data class TrnInput(
    val callerRef: CallerRef = CallerRef(),
    val assetId: String? = null,
    // Optional, specific cash balance
    val cashAssetId: String? = null,
    val trnType: TrnType = TrnType.BUY,
    val quantity: BigDecimal = BigDecimal.ZERO,
    val tradeCurrency: String = "USD",
    val cashCurrency: String? = null,
    // Trade to Portfolio Base Rate. Calculated if zero
    var tradeBaseRate: BigDecimal = BigDecimal.ZERO,
    // Trade to Cash Settlement Rate. Calculated if zero
    var tradeCashRate: BigDecimal = BigDecimal.ZERO,
    // Trade CCY to portfolio Currency. Calculated if zero
    var tradePortfolioRate: BigDecimal = BigDecimal.ZERO,
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd",
    ) @JsonSerialize(using = LocalDateSerializer::class) @JsonDeserialize(
        using = LocalDateDeserializer::class,
    )
    val tradeDate: LocalDate = DateUtils().date,
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd",
    ) @JsonSerialize(using = LocalDateSerializer::class) @JsonDeserialize(
        using = LocalDateDeserializer::class,
    )
    var settleDate: LocalDate? = null,
    // In trade Currency
    val fees: BigDecimal = BigDecimal.ZERO,
    val price: BigDecimal = BigDecimal.ZERO,
    val tradeAmount: BigDecimal = BigDecimal.ZERO,
    val tax: BigDecimal = BigDecimal.ZERO,
    // End In trade Currency
    val status: TrnStatus = TrnStatus.CONFIRMED,
    var comments: String? = null,
    val cashAmount: BigDecimal = BigDecimal.ZERO,
)
