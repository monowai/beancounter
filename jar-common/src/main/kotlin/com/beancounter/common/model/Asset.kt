package com.beancounter.common.model

import com.beancounter.common.input.AssetInput
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.persistence.Entity
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
    val code: String,
    @Id val id: String = code,
    @JsonInclude(JsonInclude.Include.NON_NULL) var name: String? = null,
    @Transient var market: Market,
    @JsonIgnore val marketCode: String = market.code,
    val priceSymbol: String? = null,
    @JsonIgnore var category: String = "Equity",
    @Transient var assetCategory: AssetCategory = AssetCategory(category, category),
    @ManyToOne val systemUser: SystemUser? = null,
    val status: Status = Status.Active,
    var version: String = "1",
) {

    companion object {
        @JvmStatic
        fun of(input: AssetInput, market: Market, status: Status = Status.Active): Asset = Asset(
            code = input.code,
            id = input.code,
            name = input.name,
            market = market,
            marketCode = market.code,
            priceSymbol = input.code,
            category = input.category,
            status = status,
        )
    }

//    constructor(code: String, market: Market, marketCode: String = market.code, status: Status = Status.Active) : this(
//        id = code,
//        code = code,
//        name = code,
//        market = market,
//        marketCode = marketCode,
//        status = status,
//    )

    // Is this asset stored locally?
    @get:JsonIgnore
    @get:Transient
    val isKnown: Boolean
        get() = !code.equals(id, ignoreCase = true)

    override fun toString(): String {
        return "Asset(code=$code, name=$name, market=$market)"
    }
}
