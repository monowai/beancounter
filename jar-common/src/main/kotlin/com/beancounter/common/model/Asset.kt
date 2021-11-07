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
data class Asset constructor(var code: String) {
    init {
        code = code.uppercase(Locale.getDefault())
    }

    @Id
    lateinit var id: String

    @JsonInclude(JsonInclude.Include.NON_NULL)
    var name: String? = null

    @JsonIgnore
    var category = "Equity"

    @Transient
    var assetCategory: AssetCategory = AssetCategory(category, category)

    // Market is managed externally as static data; marketCode alone is persisted.
    @Transient
    lateinit var market: Market

    // Caller doesn't see marketCode
    @JsonIgnore
    var marketCode: String? = null

    // Either the market providers symbol or the Currency code in the case of cash.
    var priceSymbol: String? = null
    var version: String = "1"
    var status: Status = Status.Active

    constructor(
        id: String,
        code: String,
        name: String?,
        category: String?,
        market: Market,
        marketCode: String?,
        priceSymbol: String?,
        status: Status = Status.Active
    ) : this(code) {
        this.id = id
        this.name = name
        this.category = category ?: "Equity"
        this.market = market
        this.marketCode = marketCode
        this.priceSymbol = priceSymbol
        this.status = status
    }

    constructor(input: AssetInput, market: Market, status: Status = Status.Active) :
        this(input.code, input.code, input.name, input.category, market, market.code, input.code, status)

    constructor(code: String, market: Market) : this(code) {
        this.market = market
        this.marketCode = market.code
    }

    constructor(id: String, code: String, name: String, market: Market) : this(code, market) {
        this.name = name
        this.id = id
    }

    constructor(code: String, priceSymbol: String = code, market: Market, category: String) : this(code) {
        this.market = market
        this.category = category
        this.priceSymbol = priceSymbol
    }

    // Is this asset stored locally?
    @get:JsonIgnore
    @get:Transient
    val isKnown: Boolean
        get() = !code.equals(id, ignoreCase = true)

    override fun toString(): String {
        return "Asset(code=$code, name=$name, market=$market)"
    }
}
