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
    val assetId: String? = null, // What is being traded
    val cashAssetId: String? = null, // A specific cash balance
    val trnType: TrnType = TrnType.BUY,
    val quantity: BigDecimal = BigDecimal.ZERO,
    val tradeCurrency: String = "USD",
    val cashCurrency: String? = null, // Generic cash balance
    var tradeBaseRate: BigDecimal? = null, // Trade to Portfolio Base Rate. Calculated if null
    var tradeCashRate: BigDecimal? = null, // Trade to Cash Settlement Rate. Calculated if null
    var tradePortfolioRate: BigDecimal? = null, // Trade CCY to portfolio Currency. Calculated if null
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val tradeDate: LocalDate = DateUtils().date,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var settleDate: LocalDate? = null,
    val fees: BigDecimal = BigDecimal.ZERO, // In trade Currency
    val price: BigDecimal = BigDecimal.ZERO, // In trade Currency
    val tradeAmount: BigDecimal = BigDecimal.ZERO, // In trade Currency
    val tax: BigDecimal = BigDecimal.ZERO, // In trade Currency
    val status: TrnStatus = TrnStatus.CONFIRMED,
    var comments: String? = null,
    val cashAmount: BigDecimal = BigDecimal.ZERO,
)
