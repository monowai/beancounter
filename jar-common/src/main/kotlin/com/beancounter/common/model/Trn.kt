package com.beancounter.common.model

import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Representation of a Financial Transaction.
 *
 * @author mikeh
 * @since 2019-02-07
 */
@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "batch", "callerId"])])
data class Trn(
    @Id
    var id: String = UUID.randomUUID().toString(),
    val trnType: TrnType,
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var tradeDate: LocalDate = DateUtils().date,
    @ManyToOne
    var asset: Asset,
    @Column(
        precision = 15,
        scale = 6
    )
    val quantity: BigDecimal = BigDecimal.ZERO,
    // In trade Currency - scale is to support Mutual Fund pricing.
    @Embedded
    var callerRef: CallerRef? = null,
    // In trade Currency
    @Column(
        precision = 15,
        scale = 6
    )
    var price: BigDecimal? = null,
    var tradeAmount: BigDecimal = quantity,
    @ManyToOne
    val tradeCurrency: Currency = asset.market.currency,
    @ManyToOne
    var cashAsset: Asset? = null,
    // Trade CCY to cash settlement currency
    @ManyToOne
    var cashCurrency: Currency? = cashAsset?.market?.currency,
    // Trade Currency to system Base Currency
    @Column(
        precision = 10,
        scale = 6
    )
    var tradeCashRate: BigDecimal = BigDecimal.ZERO,
    // Trade CCY to portfolio reference  currency
    @Column(
        precision = 10,
        scale = 6
    )
    var tradeBaseRate: BigDecimal = BigDecimal.ONE,
    // Signed Cash in settlement currency.
    @Column(
        precision = 10,
        scale = 6
    )
    var tradePortfolioRate: BigDecimal = BigDecimal.ONE,
    var cashAmount: BigDecimal = BigDecimal.ZERO,
    @ManyToOne
    var portfolio: Portfolio = PortfolioUtils.getPortfolio(),
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var settleDate: LocalDate? = null,
    // In trade Currency
    var fees: BigDecimal = BigDecimal.ZERO,
    // In trade Currency
    var tax: BigDecimal = BigDecimal.ZERO,
    var comments: String? = null,
    var version: String = LATEST_VERSION,
    var status: TrnStatus = TrnStatus.CONFIRMED
) {
    companion object {
        const val LATEST_VERSION: String = "3"
    }
}