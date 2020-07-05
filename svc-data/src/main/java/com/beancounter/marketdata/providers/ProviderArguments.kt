package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.AssetUtils.Companion.split
import com.beancounter.common.utils.DateUtils
import java.util.*

/**
 * MarketDataProviders have different ways to provide keys. THis helps us track our
 * internal view of an Asset and match it to the dataproviders ID that is returned in a response.
 *
 * @author mikeh
 * @since 2019-03-12
 */
class ProviderArguments(private val dataProviderConfig: DataProviderConfig) {
    private var count = 0
    var currentBatch = 0
    var date: String? = null
    var delimiter = ","
    private var batchConfigs: MutableMap<Int, BatchConfig?> = HashMap()
    private var dpToBc: MutableMap<String, Asset> = HashMap()

    /**
     * How the MarketDataProvider wants the search key for all assets to be passed.
     */
    var batch: MutableMap<Int, String> = HashMap()

    private fun bumpBatch() {
        currentBatch++
    }

    /**
     * Tracks and batches the asset for the DataProvider.
     *
     * @param asset BeanCounter Asset
     */
    fun addAsset(asset: Asset, requestedDate: String) {
        val dpKey = dataProviderConfig.getPriceCode(asset)
        val valuationDate = dateUtils.getDateString(
                dataProviderConfig.getMarketDate(asset.market, requestedDate))
        dpToBc[dpKey] = asset
        var batchConfig = batchConfigs[currentBatch]
        if (batchConfig == null) {
            batchConfig = BatchConfig(currentBatch, valuationDate)
            batchConfigs[currentBatch] = batchConfig
        }
        var searchKey = batch[batchConfig.batch]
        searchKey = if (searchKey == null) {
            dpKey
        } else {
            searchKey + delimiter + dpKey
        }
        batch[currentBatch] = searchKey
        count++
        if (count >= dataProviderConfig.getBatchSize()) {
            count = 0
            currentBatch++
        }
    }

    fun getAssets(batchId: Int?): Array<String> {
        return batch[batchId]!!.split(delimiter).toTypedArray()
    }

    fun getBatchConfigs(): Map<Int, BatchConfig?> {
        return batchConfigs
    }

    fun getDpToBc(): Map<String, Asset> {
        return dpToBc
    }

    companion object {
        private val dateUtils = DateUtils()

        /**
         * Helper to build an instance of this class based on the supplied arguments.
         *
         * @param priceRequest       Args
         * @param dataProviderConfig Who our provider is
         * @return This class with various keys cross indexed for convenience
         */
        fun getInstance(priceRequest: PriceRequest,
                        dataProviderConfig: DataProviderConfig): ProviderArguments {
            val providerArguments = ProviderArguments(dataProviderConfig)
            providerArguments.date = priceRequest.date
            // Data providers can have market dependent price dates. Batch first by market, then by size
            val marketAssets: Map<String, Collection<Asset>> = split(priceRequest.assets)
            for (market in marketAssets.keys) {
                for (asset in marketAssets[market] ?: error("This should not happen")) {
                    providerArguments.addAsset(
                            asset,
                            priceRequest.date)
                }
                providerArguments.bumpBatch()
            }
            return providerArguments
        }
    }
}