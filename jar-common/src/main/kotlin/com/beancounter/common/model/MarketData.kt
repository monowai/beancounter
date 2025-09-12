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
    @param:JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = DateUtils.FORMAT
    )
    @param:JsonSerialize(using = LocalDateSerializer::class)
    @param:JsonDeserialize(using = LocalDateDeserializer::class)
    var priceDate: LocalDate = LocalDate.now(),
    @Column(
        precision = 15,
        scale = 6
    )
    var close: BigDecimal = BigDecimal.ZERO,
    @Column(
        precision = 15,
        scale = 6
    )
    var open: BigDecimal = BigDecimal.ZERO,
    @Column(
        precision = 15,
        scale = 6
    )
    var low: BigDecimal = BigDecimal.ZERO,
    @Column(
        precision = 15,
        scale = 6
    )
    var high: BigDecimal = BigDecimal.ZERO,
    @Column(
        precision = 15,
        scale = 6
    )
    var previousClose: BigDecimal = BigDecimal.ZERO,
    @Column(
        precision = 15,
        scale = 6
    )
    var change: BigDecimal = BigDecimal.ZERO,
    @Column(
        precision = 15,
        scale = 6
    )
    var changePercent: BigDecimal = BigDecimal.ZERO,
    var volume: Int = 0,
    @Column(
        precision = 7,
        scale = 4
    )
    var dividend: BigDecimal = BigDecimal.ZERO,
    @Column(
        precision = 7,
        scale = 4
    )
    var split: BigDecimal = BigDecimal.ONE,
    var source: String = "ALPHA"
) {
    @Id
    @JsonIgnore
    val id: String = KeyGenUtils().id

    companion object {
        @JvmStatic
        fun isSplit(marketData: MarketData): Boolean = BigDecimal.ONE.compareTo(marketData.split) != 0

        @JvmStatic
        fun isDividend(marketData: MarketData): Boolean = BigDecimal.ZERO.compareTo(marketData.dividend) != 0
    }
}