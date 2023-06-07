package com.beancounter.common.model

import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Various data points representing marketdata for an asset.
 *
 * @author mikeh
 * @since 2019-01-27
 */
@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["source", "asset_id", "priceDate"])])
data class MarketData(
    @ManyToOne var asset: Asset,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DateUtils.format)
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var priceDate: LocalDate? = null,
    @Column(precision = 15, scale = 6)
    var close: BigDecimal = BigDecimal.ZERO,
    @Column(precision = 15, scale = 6)
    var open: BigDecimal? = null,
    @Column(precision = 15, scale = 6)
    var previousClose: BigDecimal? = null,

) {
    constructor(
        asset: Asset,
        source: String,
        priceDate: LocalDate?,
        open: BigDecimal?,
        close: BigDecimal = BigDecimal.ZERO,
        low: BigDecimal?,
        high: BigDecimal?,
        previousClose: BigDecimal?,
        change: BigDecimal?,
        changePercent: BigDecimal?,
        volume: Int?,
        dividend: BigDecimal = BigDecimal.ZERO,
        split: BigDecimal = BigDecimal.ONE,
    ) : this(asset, priceDate, close, open) {
        this.source = source
        this.priceDate = priceDate
        this.low = low
        this.high = high
        this.previousClose = previousClose
        this.change = change
        this.changePercent = changePercent
        this.volume = volume
        this.dividend = dividend
        this.split = split
    }

    constructor(asset: Asset, priceDate: LocalDate) : this(asset) {
        this.priceDate = priceDate
    }

    @Id
    @JsonIgnore
    val id: String = KeyGenUtils().id

    var source: String = "ALPHA"

    @Column(precision = 15, scale = 6)
    var low: BigDecimal? = null

    @Column(precision = 15, scale = 6)
    var high: BigDecimal? = null

    @Column(precision = 7, scale = 4)
    var change: BigDecimal? = null

    @Column(precision = 7, scale = 4)
    var changePercent: BigDecimal? = null
    var volume: Int? = null

    @Column(precision = 7, scale = 4)
    var dividend: BigDecimal = BigDecimal.ZERO

    @Column(precision = 7, scale = 4)
    var split: BigDecimal = BigDecimal.ONE

    companion object {
        @JvmStatic
        fun isSplit(marketData: MarketData): Boolean {
            return BigDecimal.ONE.compareTo(marketData.split) != 0
        }

        @JvmStatic
        fun isDividend(marketData: MarketData): Boolean {
            return BigDecimal.ZERO.compareTo(marketData.dividend) != 0
        }
    }
}
