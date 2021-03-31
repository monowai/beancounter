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
    val assetId: String,
    val trnType: TrnType = TrnType.BUY,
    val quantity: BigDecimal = BigDecimal.ZERO,
    val tradeCurrency: String = "USD",
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val tradeDate: LocalDate = DateUtils().date,
    val fees: BigDecimal = BigDecimal.ZERO, // In trade Currency
    val price: BigDecimal, // In trade Currency
    val tradeAmount: BigDecimal = BigDecimal.ZERO, // In trade Currency
    val comments: String? = null,

) {

    var status: TrnStatus? = null
    var cashAsset: String? = null
    var cashCurrency: String? = null

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var settleDate: LocalDate? = null
    var tax = BigDecimal.ZERO // In trade Currency

    var cashAmount: BigDecimal? = null
    var tradeCashRate: BigDecimal? = null // Trade CCY to cash settlement currency
    var tradeBaseRate: BigDecimal? = null // Trade Currency to system Base Currency
    var tradePortfolioRate: BigDecimal? = null // Trade CCY to portfolio reference  currency
}
