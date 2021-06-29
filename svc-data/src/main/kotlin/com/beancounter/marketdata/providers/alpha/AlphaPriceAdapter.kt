package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.exception.SystemException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.marketdata.providers.MarketDataAdapter
import com.beancounter.marketdata.providers.ProviderArguments
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.math.BigDecimal

/**
 * Convert Alpha MarketData to BeanCounter MarketData
 */
@Service
class AlphaPriceAdapter : MarketDataAdapter {
    final val alphaMapper: ObjectMapper = BcJson().objectMapper
    private val dateUtils = DateUtils()
    operator fun get(
        providerArguments: ProviderArguments,
        batchId: Int?,
        response: String?
    ): Collection<MarketData> {
        val results: MutableCollection<MarketData> = ArrayList()
        try {
            val assets = providerArguments.getAssets(batchId)
            for (dpAsset in assets) {
                val asset = providerArguments.getDpToBc()[dpAsset]
                setPriceResponse(asset, response, results, providerArguments)
            }
        } catch (e: IOException) {
            throw SystemException(e.message)
        }
        return results
    }

    private fun setPriceResponse(
        asset: Asset?,
        response: String?,
        results: MutableCollection<MarketData>,
        providerArguments: ProviderArguments
    ) {
        if (isMdResponse(asset, response)) {
            setPriceResponse(response, asset, results, providerArguments)
        } else {
            results.add(getDefault(asset, providerArguments))
        }
    }

    private fun setPriceResponse(
        response: String?,
        asset: Asset?,
        results: MutableCollection<MarketData>,
        providerArguments: ProviderArguments
    ) {
        val priceResponse = alphaMapper.readValue(response, PriceResponse::class.java)
        if (priceResponse != null && priceResponse.data.isNotEmpty()) {
            for (marketData in priceResponse.data) {
                marketData.asset = asset!! // Return BC view of the asset, not MarketProviders
                normalise(asset.market, marketData)
                log.trace("Valued {} ", asset.name)
                results.add(marketData)
            }
        } else {
            results.add(getDefault(asset, providerArguments))
        }
    }

    private fun normalise(market: Market, marketData: MarketData) {
        if (market.multiplier.compareTo(BigDecimal.ONE) != 0) {
            marketData.close = multiply(marketData.close, market.multiplier, 4)!!
            marketData.open = multiply(marketData.open, market.multiplier, 4)
            marketData.high = multiply(marketData.high, market.multiplier, 4)
            marketData.low = multiply(marketData.low, market.multiplier, 4)
            marketData.previousClose = multiply(marketData.previousClose, market.multiplier, 4)
            marketData.change = multiply(marketData.change, market.multiplier, 4)
        }
    }

    @Throws(IOException::class)
    private fun isMdResponse(asset: Asset?, result: String?): Boolean {
        var field: String? = null
        if (result == null) {
            return false
        }
        when {
            result.contains("Error Message") -> {
                field = "Error Message"
            }
            result.contains("\"Note\":") -> {
                field = "Note"
            }
            result.contains("\"Information\":") -> {
                field = "Information"
            }
        }
        if (field != null) {
            val resultMessage = alphaMapper.readTree(result)
            log.debug("API returned [{}] for {}", resultMessage[field], asset)
            return false
        }
        return true
    }

    private fun getDefault(asset: Asset?, providerArguments: ProviderArguments): MarketData {
        var date = providerArguments.getBatchConfigs()[0]?.date

        if (date == null) {
            date = providerArguments.date
            if (date == null)
                date = dateUtils.today()
        }
        val priceDate = dateUtils.getDate(date)

        return MarketData(asset!!, priceDate)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AlphaPriceAdapter::class.java)
    }

    init {
        val module = SimpleModule(
            "AlphaMarketDataDeserializer",
            Version(1, 0, 0, null, null, null)
        )
        module.addDeserializer(PriceResponse::class.java, AlphaPriceDeserializer())
        module.addDeserializer(AssetSearchResponse::class.java, AlphaSearchDeserializer())
        alphaMapper.registerModule(module)
    }
}
