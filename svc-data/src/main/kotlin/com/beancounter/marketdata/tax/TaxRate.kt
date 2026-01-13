package com.beancounter.marketdata.tax

import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate

/**
 * User-defined tax rate per country.
 *
 * Each SystemUser can define their own tax rates by country code.
 * This allows users to configure income tax rates for different jurisdictions
 * where they hold income-generating assets (e.g., rental properties).
 *
 * The tax rate is a simple flat rate expressed as a decimal (e.g., 0.20 for 20%).
 *
 * Used by PrivateAssetConfig when `deductIncomeTax=true` to calculate
 * net income after tax for FI/retirement planning.
 */
@Entity
@Table(
    name = "tax_rate",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_tax_rate_owner_country",
            columnNames = ["owner_id", "country_code"]
        )
    ]
)
data class TaxRate(
    @Id
    val id: String = KeyGenUtils().id,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: SystemUser,
    @Column(name = "country_code", length = 2, nullable = false)
    val countryCode: String,
    @Column(name = "rate", precision = 5, scale = 4, nullable = false)
    val rate: BigDecimal = BigDecimal.ZERO,
    @Column(name = "created_date", nullable = false)
    val createdDate: LocalDate = LocalDate.now(),
    @Column(name = "updated_date", nullable = false)
    val updatedDate: LocalDate = LocalDate.now()
)