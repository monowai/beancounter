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
    override fun enrich(id: String, market: Market, assetInput: AssetInput): Asset {
        return Asset(
            id = id,
            market = market,
            code = parseCode(systemUserService.getActiveUser()!!, assetInput.code),
            name = if (assetInput.name != null) assetInput.name!!.replace("\"", "") else null,
            category = assetInput.category,
            marketCode = market.code,
            priceSymbol = assetInput.currency,
            systemUser = systemUserService.getActiveUser(),
        )
    }

    override fun canEnrich(asset: Asset): Boolean {
        return id() == asset.market.code
    }

    override fun id(): String {
        return id
    }

    companion object {
        const val id = OffMarketDataProvider.ID

        @JvmStatic
        fun parseCode(systemUser: SystemUser, code: String) =
            if (code.startsWith(systemUser.id)) {
                code
            } else {
                "${systemUser.id}.${code.uppercase()}"
            }
    }
}
