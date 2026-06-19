package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.providers.DataProviderConfig
import tools.jackson.core.Version
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.jacksonMapperBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.LocalDate

/**
 * Helper functions for Alpha data provider. Enable dependant supporting classes
 */
@Configuration
@EnableConfigurationProperties(NewsProperties::class)
@Import(
    AlphaPriceService::class,
    AlphaProxy::class,
    AlphaPriceAdapter::class
)
class AlphaConfig(
    val dateUtils: DateUtils = DateUtils(),
    val marketUtils: PreviousClosePriceDate = PreviousClosePriceDate(DateUtils())
) : DataProviderConfig {
    @Value("\${beancounter.market.providers.alpha.markets}")
    var markets: String? = null

    companion object {
        // Jackson 3 mappers are immutable; build once with all modules. The custom
        // SimpleModule carries the Alpha deserializers alongside the Kotlin module.
        private val alphaModule: SimpleModule =
            SimpleModule(
                "AlphaMarketDataDeserializer",
                Version(
                    1,
                    0,
                    0,
                    null,
                    null,
                    null
                )
            ).apply {
                addDeserializer(
                    PriceResponse::class.java,
                    AlphaPriceDeserializer()
                )
                addDeserializer(
                    AssetSearchResponse::class.java,
                    AlphaSearchDeserializer()
                )
            }

        private val alphaMapper: ObjectMapper =
            jacksonMapperBuilder()
                .addModule(alphaModule)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build()
    }

    override fun getBatchSize() = 1

    fun translateMarketCode(market: Market): String? {
        if (isNullMarket(market.code)) {
            return null
        }
        return when (market.code.uppercase()) {
            "ASX" -> "AX"
            "TSX" -> "TRT"
            else -> market.code
        }
    }

    val nullMarket = "NASDAQ|NYSE|AMEX|US"

    fun isNullMarket(marketCode: String) =
        nullMarket.contains(
            marketCode,
            true
        )

    /**
     * Resolves the Date to use when querying the market.
     * This will be previous days CoB until the market is closed.
     */
    override fun getMarketDate(
        market: Market,
        date: String,
        currentMode: Boolean
    ): LocalDate =
        marketUtils.getPriceDate(
            dateUtils.offsetNow(date).toZonedDateTime(),
            market,
            currentMode
        )

    override fun getPriceCode(asset: Asset): String {
        if (asset.priceSymbol != null) {
            return asset.priceSymbol!!
        }
        // Index symbols (e.g. ^GSPC, ^IXIC) carry no market suffix on Alpha Vantage.
        if (asset.code.startsWith("^")) {
            return asset.code
        }
        val marketCode = translateMarketCode(asset.market)
        return if (!marketCode.isNullOrEmpty()) {
            asset.code + "." + marketCode
        } else {
            asset.code
        }
    }

    fun translateSymbol(code: String): String =
        code.replace(
            ".",
            "-"
        )

    fun getObjectMapper(): ObjectMapper = alphaMapper
}