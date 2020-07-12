package com.beancounter.common.input

import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import java.math.BigDecimal
import java.time.LocalDate

data class TrnInput(val callerRef: CallerRef, val assetId: String, val trnType: TrnType, val quantity: BigDecimal) {
    constructor(callerRef: CallerRef, assetId: String) : this(callerRef, assetId, TrnType.BUY, BigDecimal.ZERO)

    var status: TrnStatus? = null
    var cashAsset: String? = null
    var tradeCurrency: String? = null
    var cashCurrency: String? = null

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var tradeDate: LocalDate? = null

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var settleDate: LocalDate? = null
    var price // In trade Currency
            : BigDecimal? = null
    var fees = BigDecimal.ZERO // In trade Currency
    var tax = BigDecimal.ZERO // In trade Currency
    var tradeAmount = BigDecimal.ZERO // In trade Currency
    var cashAmount: BigDecimal? = null
    var tradeCashRate // Trade CCY to cash settlement currency
            : BigDecimal? = null
    var tradeBaseRate // Trade Currency to system Base Currency
            : BigDecimal? = null
    var tradePortfolioRate // Trade CCY to portfolio reference  currency
            : BigDecimal? = null
    var comments: String? = null


}