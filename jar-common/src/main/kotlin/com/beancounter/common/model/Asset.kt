package com.beancounter.common.model

import com.beancounter.common.input.AssetInput
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import java.util.Locale
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Transient
import javax.persistence.UniqueConstraint

/**
 * Persistent representation of an instrument traded on a market.
 *
 * @author mikeh
 * @since 2019-01-27
 */
@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["code", "marketCode"])])
data class Asset constructor(
    @Id val id: String,
    var code: String,
    @JsonInclude(JsonInclude.Include.NON_NULL) var name: String?,
    @Transient var market: Market,
    @JsonIgnore val marketCode: String? = null,
    val priceSymbol: String? = null,
    @JsonIgnore var category: String = "Equity",
    @Transient var assetCategory: AssetCategory = AssetCategory(category, category),
    val status: Status = Status.Active,
    var version: String = "1",
) {
    init {
        code = code.uppercase(Locale.getDefault())
    }

    constructor(input: AssetInput, market: Market, status: Status = Status.Active) : this(
        id = input.code,
        code = input.code,
        name = input.name,
        category = input.category,
        market = market,
        marketCode = market.code,
        priceSymbol = input.code,
        status = status,
    )

    constructor(code: String, market: Market, marketCode: String? = null) : this(
        id = code.uppercase(),
        code = code,
        name = code,
        market = market,
        marketCode = marketCode,
    )

    // Is this asset stored locally?
    @get:JsonIgnore
    @get:Transient
    val isKnown: Boolean
        get() = !code.equals(id, ignoreCase = true)

    override fun toString(): String {
        return "Asset(code=$code, name=$name, market=$market)"
    }
}
