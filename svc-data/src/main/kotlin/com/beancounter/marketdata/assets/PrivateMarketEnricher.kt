package com.beancounter.marketdata.assets

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.providers.custom.PrivateMarketDataProvider
import com.beancounter.marketdata.registration.SystemUserService
import org.springframework.stereotype.Service

/**
 * Enricher for private market assets that are created for users.
 */
@Service
class PrivateMarketEnricher(
    private val systemUserService: SystemUserService,
    private val accountingTypeService: AccountingTypeService,
    private val currencyService: CurrencyService
) : AssetEnricher {
    override fun enrich(
        id: String,
        market: Market,
        assetInput: AssetInput
    ): Asset {
        // Trusted callers (e.g. the RabbitMQ CSV-import consumer) supply the owner on the
        // AssetInput and run on a thread with no JWT in scope. Prefer that owner so the
        // resolution doesn't hit getOrThrow → "Not authorised" (DATA-4Z). The interactive
        // HTTP path leaves owner blank and falls back to the authenticated caller.
        val systemUser =
            assetInput.owner
                .takeIf { it.isNotBlank() }
                ?.let { systemUserService.findById(it) }
                ?: systemUserService.getOrThrow()
        val currencyCode =
            assetInput.currency
                ?: throw BusinessException("Currency required for private asset ${assetInput.code}")
        val currency = currencyService.getCode(currencyCode)
        val accountingType =
            accountingTypeService.getOrCreate(
                category = assetInput.category,
                currency = currency
            )
        return Asset(
            code =
                parseCode(
                    systemUser,
                    assetInput.code
                ),
            id = id,
            name =
                assetInput.name?.replace(
                    "\"",
                    ""
                ),
            market = market,
            marketCode = market.code,
            category = assetInput.category,
            accountingType = accountingType,
            systemUser = systemUser
        )
    }

    override fun canEnrich(asset: Asset): Boolean = id() == asset.market.code

    override fun id(): String = ID

    companion object {
        const val ID = PrivateMarketDataProvider.ID

        @JvmStatic
        fun parseCode(
            systemUser: SystemUser,
            code: String
        ) = if (code.startsWith(systemUser.id)) {
            code
        } else {
            "${systemUser.id}.${code.uppercase()}"
        }
    }
}