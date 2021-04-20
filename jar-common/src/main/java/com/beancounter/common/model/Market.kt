package com.beancounter.common.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer
import org.springframework.boot.context.properties.ConstructorBinding
import java.math.BigDecimal
import java.time.LocalTime
import java.util.TimeZone

/**
 * A stock exchange.
 *
 * @author mikeh
 * @since 2019-01-27
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Market @ConstructorBinding constructor(
    val code: String,
    val currencyId: String = "USD",
    val timezoneId: String = "US/Eastern",
    @JsonSerialize(using = LocalTimeSerializer::class)
    @JsonDeserialize(using = LocalTimeDeserializer::class)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    val priceTime: LocalTime = LocalTime.of(19, 0),
    val daysToSubtract: Int = 1,
    val aliases: Map<String, String> = HashMap(),
    val enricher: String? = null,
    val multiplier: BigDecimal = BigDecimal("1.0")
) {

    constructor(code: String, currency: Currency) : this(code, currency.code) {
        this.currency = currency
    }

    val timezone: TimeZone = TimeZone.getTimeZone(timezoneId)
    @Transient
    var currency: Currency = Currency(currencyId)

    @JsonIgnore
    fun inMemory(): Boolean {
        return code.equals("MOCK", ignoreCase = true)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Market

        if (code != other.code) return false
        if (currencyId != other.currencyId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + currencyId.hashCode()
        return result
    }
}
