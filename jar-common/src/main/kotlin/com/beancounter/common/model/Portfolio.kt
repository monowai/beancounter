package com.beancounter.common.model

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.jpa.domain.AbstractPersistable_.id
import java.math.BigDecimal

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
    val marketValue: BigDecimal = BigDecimal.ZERO,
    val irr: BigDecimal = BigDecimal.ZERO,
    @ManyToOne val currency: Currency = Currency("USD"),
    @ManyToOne val base: Currency = currency,
    @ManyToOne var owner: SystemUser = SystemUser(id),
)
