package com.beancounter.common.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Portfolio that owns transactions on behalf of a systemUser
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["code", "owner_id"])])
data class Portfolio(
    @Id val id: String,
    val code: String = id,
    val name: String = code,
    val active: Boolean = true,
    @Column(
        precision = 15,
        scale = 6
    )
    val marketValue: BigDecimal = BigDecimal.ZERO,
    @Column(
        precision = 15,
        scale = 6
    )
    val irr: BigDecimal = BigDecimal.ZERO,
    @Column(
        precision = 15,
        scale = 4
    )
    val gainOnDay: BigDecimal? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    val assetClassification: Map<String, BigDecimal>? = null,
    val valuedAt: LocalDate? = null,
    @ManyToOne val currency: Currency = Currency("USD"),
    @ManyToOne val base: Currency = currency,
    @ManyToOne var owner: SystemUser = SystemUser(id)
)