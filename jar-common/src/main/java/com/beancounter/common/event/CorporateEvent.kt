package com.beancounter.common.event

import com.beancounter.common.model.TrnType
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import java.math.BigDecimal
import java.time.LocalDate
import javax.persistence.*

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["assetId", "recordDate"])])
data class CorporateEvent constructor(
        @Id val id: String?,
        val trnType: TrnType,
        val source: String,
        var assetId: String,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @JsonSerialize(using = LocalDateSerializer::class)
        @JsonDeserialize(using = LocalDateDeserializer::class)
        var recordDate: LocalDate,

        @Column(precision = 15, scale = 4) var rate: BigDecimal? = null,
        @Column(precision = 15, scale = 4) var split: BigDecimal? = null,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @JsonSerialize(using = LocalDateSerializer::class)
        @JsonDeserialize(using = LocalDateDeserializer::class)
        val payDate: LocalDate? = null
) {
    constructor(trnType: TrnType,
                source: String,
                assetId: String,
                recordDate: LocalDate,
                rate: BigDecimal) :
            this(null,
                    trnType,
                    source,
                    assetId,
                    recordDate,
                    rate,
                    BigDecimal("1.0000"),
                    recordDate)

}