package com.beancounter.common.model

import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.annotation.JsonFormat
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
 * Representation of an FX Rate on a given date.
 */
@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["from_id", "to_id", "date"])])
data class FxRate(
    @ManyToOne val from: Currency,
    @ManyToOne val to: Currency = from,
    @Column(precision = 15, scale = 6) val rate: BigDecimal = BigDecimal.ONE,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DateUtils.FORMAT)
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val date: LocalDate = LocalDate.now(),
    @Id val id: String = "${from.code}-${to.code}-$date",
)
