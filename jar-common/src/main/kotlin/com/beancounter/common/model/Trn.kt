package com.beancounter.common.model

import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import java.math.BigDecimal
import java.time.LocalDate
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
    val trnType: TrnType,
    @ManyToOne
    var asset: Asset,
    @Column(precision = 15, scale = 6)
    val quantity: BigDecimal = BigDecimal.ZERO,
    @ManyToOne
    val tradeCurrency: Currency = asset.market.currency,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var tradeDate: LocalDate = DateUtils().date

) {

    @Id
    var id: String? = null

    @Embedded
    var callerRef: CallerRef? = null
    var status: TrnStatus? = TrnStatus.CONFIRMED

    @ManyToOne
    var portfolio: Portfolio = Portfolio("UNDEFINED")

    @ManyToOne
    var cashAsset: Asset? = null

    @ManyToOne
    var cashCurrency: Currency? = null

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var settleDate: LocalDate? = null

    // In trade Currency - scale is to support Mutual Fund pricing.
    @Column(precision = 15, scale = 6)
    var price: BigDecimal? = null

    // In trade Currency
    var fees: BigDecimal = BigDecimal.ZERO

    // In trade Currency
    var tax: BigDecimal = BigDecimal.ZERO

    // In trade Currency
    var tradeAmount: BigDecimal = BigDecimal.ZERO

    // Trade amount in settlement currency.
    var cashAmount: BigDecimal? = null

    // Trade CCY to cash settlement currency
    @Column(precision = 10, scale = 6)
    var tradeCashRate: BigDecimal? = null

    // Trade Currency to system Base Currency
    @Column(precision = 10, scale = 6)
    var tradeBaseRate: BigDecimal? = null

    // Trade CCY to portfolio reference  currency
    @Column(precision = 10, scale = 6)
    var tradePortfolioRate: BigDecimal? = null
    var version: String? = "1"
    var comments: String? = null

    constructor(
        id: String?,
        callerRef: CallerRef?,
        trnType: TrnType,
        status: TrnStatus?,
        portfolio: Portfolio,
        asset: Asset,
        cashAsset: Asset?,
        tradeCurrency: Currency,
        cashCurrency: Currency?,
        tradeDate: LocalDate,
        settleDate: LocalDate?,
        quantity: BigDecimal,
        price: BigDecimal?,
        fees: BigDecimal,
        tax: BigDecimal,
        tradeAmount: BigDecimal,
        cashAmount: BigDecimal?,
        tradeCashRate: BigDecimal?,
        tradeBaseRate: BigDecimal?,
        tradePortfolioRate: BigDecimal?,
        version: String?,
        comments: String?
    ) : this(trnType, asset, quantity, tradeCurrency, tradeDate) {
        this.id = id
        this.callerRef = callerRef
        this.status = status
        this.portfolio = portfolio
        this.cashAsset = cashAsset
        this.cashCurrency = cashCurrency
        this.settleDate = settleDate
        this.price = price
        this.fees = fees
        this.tax = tax
        this.tradeAmount = tradeAmount
        this.cashAmount = cashAmount
        this.tradeCashRate = tradeCashRate
        this.tradeBaseRate = tradeBaseRate
        this.tradePortfolioRate = tradePortfolioRate
        this.version = version
        this.comments = comments
    }
}
