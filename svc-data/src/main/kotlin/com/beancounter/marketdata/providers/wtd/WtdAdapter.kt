package com.beancounter.marketdata.providers.wtd

import com.beancounter.common.exception.SystemException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.DatedBatch
import com.beancounter.marketdata.providers.MarketDataAdapter
import com.beancounter.marketdata.providers.ProviderArguments
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

/**
 * Not used.  Can be refactored to support the replacement service MarketStack.
 */
@Service
class WtdAdapter : MarketDataAdapter {
    private val dateUtils = DateUtils()

    operator fun get(
        providerArguments: ProviderArguments,
        batchId: Int,
        response: Future<WtdResponse>?,
    ): Collection<MarketData> {
        val results: MutableCollection<MarketData> = ArrayList()
        return try {
            val (date, data, message) = response!!.get()
            val assets = providerArguments.getAssets(batchId)
            val batchConfig = providerArguments.getBatchConfigs()[batchId]
            for (dpAsset in assets) {
                // Ensure we return a MarketData result for each requested asset
                val bcAsset = providerArguments.getDpToBc()[dpAsset]
                if (message != null) {
                    // Issue with the data
                    // ToDo: subtract a day and try again?
                    log.trace("{} - {}", message, providerArguments.getAssets(batchId))
                }
                var marketData: MarketData? = null
                val wtdMarketData = data[dpAsset]
                if (wtdMarketData != null) {
                    marketData = MarketData(bcAsset!!, dateUtils.getDate(date.toString()))
                    marketData.close = wtdMarketData.close
                    marketData.high = wtdMarketData.high
                    marketData.low = wtdMarketData.low
                    marketData.open = wtdMarketData.open
                    marketData.volume = wtdMarketData.volume
                }
                if (marketData == null) {
                    // Not contained in the response
                    marketData = getDefault(bcAsset, dpAsset, batchConfig)
                }
                results.add(marketData)
            }
            results
        } catch (e: InterruptedException) {
            throw SystemException(e.message)
        } catch (e: ExecutionException) {
            throw SystemException(e.message)
        }
    }

    private fun getDefault(
        asset: Asset?,
        dpAsset: String,
        datedBatch: DatedBatch?,
    ): MarketData {
        log.trace(
            "{}/{} - unable to locate a price on {}",
            dpAsset,
            asset!!.name,
            datedBatch!!.date,
        )
        val result = MarketData(asset)
        result.close = BigDecimal.ZERO
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(WtdAdapter::class.java)
    }
}
