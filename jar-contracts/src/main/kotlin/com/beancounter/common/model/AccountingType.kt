package com.beancounter.common.model

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * Accounting type combining category, currency, and settlement metadata.
 * Unique per (category, currency) — multiple assets share the same AccountingType.
 *
 * Examples: "USD Equity", "NZD Cash", "SGD Real Estate"
 */
@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["category", "currency_code"])])
data class AccountingType(
    @Id
    val id: String,
    val category: String,
    @ManyToOne
    val currency: Currency,
    val boardLot: Int = 1,
    val settlementDays: Int = 1
) {
    override fun toString(): String = "${currency.code} $category"
}