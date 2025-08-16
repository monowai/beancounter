package com.beancounter.common.event

import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["assetId", "recordDate"])])
/**
 * Representation of a Corporate Action or Event.
 */
data class CorporateEvent(
    @Id val id: String? = null,
    val trnType: TrnType = TrnType.DIVI,
    val source: String = "ALPHA",
    var assetId: String,
    @param:JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    @param:JsonSerialize(using = LocalDateSerializer::class)
    @param:JsonDeserialize(using = LocalDateDeserializer::class)
    var recordDate: LocalDate = DateUtils().date,
    @Column(
        precision = 15,
        scale = 4
    )
    var rate: BigDecimal = BigDecimal.ZERO,
    @Column(
        precision = 15,
        scale = 4
    )
    var split: BigDecimal = BigDecimal("1.0000"),
    @param:JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    @param:JsonSerialize(using = LocalDateSerializer::class)
    @param:JsonDeserialize(using = LocalDateDeserializer::class)
    val payDate: LocalDate = recordDate
)