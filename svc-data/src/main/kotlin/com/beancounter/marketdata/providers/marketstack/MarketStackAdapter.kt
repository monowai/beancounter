package com.beancounter.marketdata.providers.marketstack

import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.providers.ProviderArguments
import com.beancounter.marketdata.providers.marketstack.model.MarketStackData
import com.beancounter.marketdata.providers.marketstack.model.MarketStackResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Adapter class for converting MarketStack API responses to internal MarketData objects.
 * This class handles the transformation of data received from the MarketStack API into
 * the format used by the application. It includes methods for converting individual
 * data points and handling errors in the API response.
 *
 * The main method `toMarketData` processes a batch of data and returns a collection
 * of MarketData objects. It also includes helper methods for creating default
 * MarketData objects when the API response contains errors or missing data.
 *
 */
@Service
class MarketStackAdapter {
    private val log = LoggerFactory.getLogger(MarketStackAdapter::class.java)

    fun toMarketData(
        providerArguments: ProviderArguments,
        batchId: Int,
        response: MarketStackResponse
    ): Collection<MarketData> {
        val results: MutableCollection<MarketData> = mutableListOf()
        response.data.forEach { data ->
            if (response.error != null) {
                log.trace(
                    "{} - {}",
                    response.error.message,
                    providerArguments.getAssets(batchId)
                )
                results.add(
                    getDefault(
                        providerArguments.getAsset(data.symbol),
                        data.symbol,
                        providerArguments.date
                    )
                )
            } else {
                results.add(
                    getMarketData(
                        data,
                        providerArguments
                    )
                )
            }
        }
        return results
    }

    private fun getMarketData(
        marketStackData: MarketStackData,
        providerArguments: ProviderArguments
    ): MarketData {
        val bcAsset = providerArguments.getAsset(marketStackData.symbol)
        if (marketStackData.close == BigDecimal.ZERO) {
            return getDefault(
                bcAsset,
                marketStackData.symbol,
                providerArguments.date
            )
        } else {
            val marketData =
                MarketData(
                    asset = bcAsset,
                    priceDate = marketStackData.date.toLocalDate(),
                    close = marketStackData.close,
                    open = marketStackData.open,
                    source = MarketStackService.ID
                )
            marketData.high = marketStackData.high
            marketData.low = marketStackData.low
            marketData.volume = marketStackData.volume
            return marketData
        }
    }

    fun getMsDefault(
        asset: String,
        exchange: String,
        date: LocalDateTime
    ): MarketStackData {
        log.trace(
            "{} - unable to locate a price",
            asset
        )
        return MarketStackData(
            symbol = asset,
            exchange = exchange,
            date = date,
            close = BigDecimal.ZERO
        )
    }

    fun getDefault(
        asset: Asset,
        dpAsset: String,
        priceDate: String
    ): MarketData {
        log.trace(
            "{}/{} - unable to locate a price on {}",
            dpAsset,
            asset.name,
            priceDate
        )
        val result =
            MarketData(
                asset,
                source = MarketStackService.ID
            )
        result.close = BigDecimal.ZERO
        return result
    }
}