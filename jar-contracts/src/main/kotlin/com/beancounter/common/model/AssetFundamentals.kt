package com.beancounter.common.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One fiscal-year fundamentals snapshot per asset, keyed by the asset id so a refresh overwrites
 * rather than accumulates. Sourced today from SEC EDGAR `companyfacts` (XBRL), hence the
 * `source` discriminator for when another provider lands.
 *
 * Every measure is nullable: a filer that does not report a given XBRL tag leaves the column
 * null, it never blocks the row. Monetary values are in the filer's reporting currency (USD for
 * SEC registrants); the entity does not carry a currency because the only source today is USD.
 */
@Entity
@Table(name = "asset_fundamentals")
data class AssetFundamentals(
    @Id
    val assetId: String,
    val source: String = SOURCE_SEC,
    /** Period end of the fiscal year the measures describe. */
    val fiscalYearEnd: LocalDate,
    /**
     * The filer's own fiscal-year label (XBRL `fy`), kept beside [fiscalYearEnd] deliberately:
     * for a non-calendar fiscal year the two differ (a January 2026 year end is commonly
     * labelled FY2025), and the label is what the filer's reports and the SEC frames use.
     */
    val fiscalYear: Int,
    @Column(precision = 19, scale = 4)
    val epsDiluted: BigDecimal? = null,
    @Column(precision = 24, scale = 2)
    val revenue: BigDecimal? = null,
    @Column(precision = 24, scale = 2)
    val netIncome: BigDecimal? = null,
    @Column(precision = 19, scale = 6)
    val dividendsPerShare: BigDecimal? = null,
    val sharesOutstanding: Long? = null,
    /** Date this snapshot was taken from the provider. */
    val asOf: LocalDate
) {
    companion object {
        const val SOURCE_SEC = ClassificationStandard.PROVIDER_SEC
    }
}