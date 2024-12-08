package com.beancounter.common.input

import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import java.time.LocalDate

/**
 * Sent by seriously trusted clients as it allows unchecked access to any portfolio.
 *
 * Used to locate transactions by various criteria.
 */
data class TrustedTrnQuery(
    val portfolio: Portfolio,
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd",
    )
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val tradeDate: LocalDate = DateUtils().date,
    val assetId: String,
) {
    override fun toString(): String = "TrustedTrnQuery(portfolio=$portfolio, tradeDate=$tradeDate, assetId=$assetId)"
}
