package com.beancounter.common.model

import com.beancounter.common.input.AssetInput
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.UniqueConstraint

/**
 * Persistent representation of an instrument traded on a market.
 *
 * @author mikeh
 * @since 2019-01-27
 */
@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["code", "marketCode"])])
data class Asset(
    var code: String,
    @Id val id: String = code,
    @JsonInclude(JsonInclude.Include.NON_NULL) var name: String? = null,
    @Transient var market: Market,
    @JsonIgnore val marketCode: String = market.code,
    var priceSymbol: String? = null,
    @JsonIgnore var category: String = "Equity",
    /**
     * Higher-level category for reporting/grouping purposes.
     * If null, falls back to mapping from [category] using [AssetCategory.toReportCategory].
     * This allows backward compatibility - existing assets work without migration.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    var reportCategory: String? = null,
    @Transient var assetCategory: AssetCategory =
        AssetCategory(
            category,
            category
        ),
    @ManyToOne val systemUser: SystemUser? = null,
    @Enumerated(EnumType.STRING)
    val status: Status = Status.Active,
    var version: String = "1",
    /**
     * Sector classification (e.g., "Information Technology", "Health Care").
     * Transient - populated during valuation from classification service.
     */
    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    var sector: String? = null,
    /**
     * Industry classification (e.g., "Software", "Biotechnology").
     * Transient - populated during valuation from classification service.
     */
    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    var industry: String? = null
) {
    companion object {
        @JvmStatic
        fun of(
            input: AssetInput,
            market: Market,
            status: Status = Status.Active
        ): Asset =
            Asset(
                code = input.code,
                id = input.code,
                name = input.name,
                market = market,
                marketCode = market.code,
                priceSymbol = input.code,
                category = input.category,
                status = status
            )
    }

    // Is this asset stored locally?
    @get:JsonIgnore
    @get:Transient
    val isKnown: Boolean
        get() =
            !code.equals(
                id,
                ignoreCase = true
            )

    /**
     * Returns the effective report category for grouping/display.
     * Uses [reportCategory] if explicitly set, otherwise maps from [assetCategory.id].
     * Serialized to JSON but ignored during deserialization (read-only).
     */
    @get:Transient
    @get:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    val effectiveReportCategory: String
        get() = reportCategory ?: AssetCategory.toReportCategory(assetCategory.id)

    override fun toString(): String = "Asset(code=$code, name=$name)"
}