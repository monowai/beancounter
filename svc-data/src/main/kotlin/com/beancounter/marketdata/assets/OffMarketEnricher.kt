package com.beancounter.marketdata.assets

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.providers.custom.OffMarketDataProvider
import com.beancounter.marketdata.registration.SystemUserService
import org.springframework.stereotype.Service

/**
 * Enricher for custom assets that are created for users
 *
 * There are no external dependencies for this Enricher.
 */
@Service
class OffMarketEnricher(
    private val systemUserService: SystemUserService,
) : AssetEnricher {
    override fun enrich(
        id: String,
        market: Market,
        assetInput: AssetInput,
    ): Asset {
        val systemUser = systemUserService.getOrThrow
        return Asset(
            code = parseCode(systemUser, assetInput.code),
            id = id,
            name = if (assetInput.name != null) assetInput.name!!.replace("\"", "") else null,
            market = market,
            marketCode = market.code,
            priceSymbol = assetInput.currency,
            category = assetInput.category,
            systemUser = systemUser,
        )
    }

    override fun canEnrich(asset: Asset): Boolean {
        return id() == asset.market.code
    }

    override fun id(): String {
        return ID
    }

    companion object {
        const val ID = OffMarketDataProvider.ID

        @JvmStatic
        fun parseCode(
            systemUser: SystemUser,
            code: String,
        ) = if (code.startsWith(systemUser.id)) {
            code
        } else {
            "${systemUser.id}.${code.uppercase()}"
        }
    }
}
