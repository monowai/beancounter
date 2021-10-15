package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.AssetUtils.Companion.split
import com.beancounter.common.utils.DateUtils

/**
 * MarketDataProviders have different ways to provide keys. THis helps us track our
 * internal view of an Asset and match it to the data providers ID that is returned in a response.
 *
 * @author mikeh
 * @since 2019-03-12
 */
class ProviderArguments(private val dataProviderConfig: DataProviderConfig) {
    private var count = 0
    private var currentBatch = 0
    var date: String = "today"
    private var delimiter = ","
    private var datedBatches: MutableMap<Int, DatedBatch> = HashMap()
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
     * Requests are grouped into the valuation date.
     *
     * @param asset BeanCounter Asset
     */
    fun addAsset(asset: Asset, requestedDate: String) {
        val dpKey = dataProviderConfig.getPriceCode(asset)
        var valuationDate = requestedDate
        if (dateUtils.isToday(requestedDate)) {
            valuationDate = dateUtils.offsetDateString(requestedDate)
        }
        dpToBc[dpKey] = asset
        var datedBatch = datedBatches[currentBatch]
        if (datedBatch == null) {
            datedBatch = DatedBatch(currentBatch, valuationDate)
            datedBatches[currentBatch] = datedBatch
        }
        var searchKey = batch[datedBatch.batchId]
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

    fun getBatchConfigs(): Map<Int, DatedBatch?> {
        return datedBatches
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
        fun getInstance(
            priceRequest: PriceRequest,
            dataProviderConfig: DataProviderConfig
        ): ProviderArguments {
            val providerArguments = ProviderArguments(dataProviderConfig)
            providerArguments.date = priceRequest.date
            // Data providers can have market dependent price dates. Batch first by market, then by size
            val marketAssets: Map<String, Collection<AssetInput>> = split(priceRequest.assets)
            for (market in marketAssets.keys) {
                for (asset in marketAssets[market] ?: error("This should not happen")) {
                    if (asset.resolvedAsset != null) {
                        providerArguments.addAsset(
                            asset.resolvedAsset!!,
                            priceRequest.date
                        )
                    }
                }
                providerArguments.bumpBatch()
            }
            return providerArguments
        }
    }
}
