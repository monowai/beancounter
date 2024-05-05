package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.providers.DataProviderConfig
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.LocalDate

/**
 * Helper functions for Alpha data provider. Enable dependant supporting classes
 */
@Configuration
@Import(AlphaPriceService::class, AlphaProxy::class, AlphaPriceAdapter::class)
class AlphaConfig(
    val dateUtils: DateUtils = DateUtils(),
    val marketUtils: PreviousClosePriceDate = PreviousClosePriceDate(DateUtils()),
) : DataProviderConfig {
    @Value("\${beancounter.market.providers.ALPHA.markets}")
    var markets: String? = null

    companion object {
        private val alphaMapper: ObjectMapper = ObjectMapper().registerKotlinModule()
    }

    init {
        val module =
            SimpleModule(
                "AlphaMarketDataDeserializer",
                Version(1, 0, 0, null, null, null),
            )
        module.addDeserializer(PriceResponse::class.java, AlphaPriceDeserializer())
        module.addDeserializer(AssetSearchResponse::class.java, AlphaSearchDeserializer())
        alphaMapper.registerModule(module)
    }

    override fun getBatchSize() = 1

    fun translateMarketCode(market: Market): String? {
        if (isNullMarket(market.code)) {
            return null
        }
        return if (market.code.equals("ASX", ignoreCase = true)) {
            "AX"
        } else {
            market.code
        }
    }

    val nullMarket = "NASDAQ|NYSE|AMEX|US"

    fun isNullMarket(marketCode: String) = nullMarket.contains(marketCode, true)

    /**
     * Resolves the Date to use when querying the market.
     * This will be previous days CoB until the market is closed.
     */
    override fun getMarketDate(
        market: Market,
        date: String,
        currentMode: Boolean,
    ): LocalDate =
        marketUtils.getPriceDate(
            dateUtils.offsetNow(date).toZonedDateTime(),
            market,
            currentMode,
        )

    override fun getPriceCode(asset: Asset): String {
        if (asset.priceSymbol != null) {
            return asset.priceSymbol!!
        }
        val marketCode = translateMarketCode(asset.market)
        return if (!marketCode.isNullOrEmpty()) {
            asset.code + "." + marketCode
        } else {
            asset.code
        }
    }

    fun translateSymbol(code: String): String = code.replace(".", "-")

    fun getObjectMapper(): ObjectMapper = alphaMapper
}
