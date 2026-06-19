package com.beancounter.common.event

import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Representation of a Corporate Action or Event.
 */
@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["assetId", "recordDate"])])
data class CorporateEvent(
    @Id val id: String? = null,
    @Enumerated(EnumType.STRING)
    val trnType: TrnType = TrnType.DIVI,
    val source: String = "ALPHA",
    var assetId: String,
    @param:JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
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
    val payDate: LocalDate = recordDate
)