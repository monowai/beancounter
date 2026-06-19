package com.beancounter.common.model

import com.beancounter.common.utils.KeyGenUtils
import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * User of this service that is Authenticated.  SystemUsers can own portfolios.
 */
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(name = "uk_system_user_email", columnNames = ["email"])
    ]
)
data class SystemUser(
    @Id var id: String = KeyGenUtils().id,
    val email: String = id,
    var active: Boolean = true,
    val auth0: String = "",
    val googleId: String = "",
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    val since: LocalDate = LocalDate.now(),
    // Per-user default funding portfolio. Used by auto-settle when a
    // Portfolio.cashPortfolioId override is not set. null disables auto-settle.
    @Column(name = "cash_portfolio_id")
    var cashPortfolioId: String? = null
)