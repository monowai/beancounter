package com.beancounter.common.model

import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Embedded
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.UniqueConstraint

/**
 * Representation of a Financial Transaction.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "batch", "callerId"])])
data class Trn constructor(
    @Id
    var id: String = UUID.randomUUID().toString(),
    val trnType: TrnType,
    @ManyToOne
    var asset: Asset,
    @Column(precision = 15, scale = 6)
    val quantity: BigDecimal = BigDecimal.ZERO,
    @Embedded
    var callerRef: CallerRef? = null,
    @Column(precision = 15, scale = 6)
    var price: BigDecimal? = null, // In trade Currency - scale is to support Mutual Fund pricing.
    var tradeAmount: BigDecimal = quantity, // In trade Currency
    @ManyToOne
    val tradeCurrency: Currency = asset.market.currency,
    @ManyToOne
    var cashAsset: Asset? = null,
    @ManyToOne
    var cashCurrency: Currency? = null,
    @Column(precision = 10, scale = 6)
    var tradeCashRate: BigDecimal = BigDecimal.ZERO, // Trade CCY to cash settlement currency
    @Column(precision = 10, scale = 6)
    var tradeBaseRate: BigDecimal = BigDecimal.ONE, // Trade Currency to system Base Currency
    @Column(precision = 10, scale = 6)
    var tradePortfolioRate: BigDecimal = BigDecimal.ONE, // Trade CCY to portfolio reference  currency
    var cashAmount: BigDecimal = BigDecimal.ZERO, // Signed Cash in settlement currency.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var tradeDate: LocalDate = DateUtils().date,
    @ManyToOne
    var portfolio: Portfolio = Portfolio("UNDEFINED"),
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var settleDate: LocalDate? = null,
    var fees: BigDecimal = BigDecimal.ZERO, // In trade Currency
    var tax: BigDecimal = BigDecimal.ZERO, // In trade Currency
    var comments: String? = null,
    var version: String = latestVersion,
    var status: TrnStatus = TrnStatus.CONFIRMED,

) {

    companion object {
        const val latestVersion: String = "2"
    }
}
