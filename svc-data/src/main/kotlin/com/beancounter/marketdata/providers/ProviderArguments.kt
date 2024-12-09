package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceRequest
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
class ProviderArguments(
    private val dataProviderConfig: DataProviderConfig,
    private val dateUtils: DateUtils = DateUtils()
) {
    private var count = 0
    private var currentBatch = 0
    var date: String = "today"
    private var delimiter = ","
    private var datedBatches: MutableMap<Int, DatedBatch> = mutableMapOf()
    private var dpToBc: MutableMap<String, Asset> = mutableMapOf()

    /**
     * How the MarketDataProvider wants the search key for all assets to be passed.
     */
    var batch: MutableMap<Int, String> = mutableMapOf()

    private fun bumpBatch() = currentBatch++

    /**
     * Tracks and batches the asset for the DataProvider.
     * Requests are grouped into the valuation date.
     *
     * @param asset BeanCounter Asset
     */
    fun batchAsset(
        asset: Asset,
        requestedDate: String
    ) {
        val dpKey = dataProviderConfig.getPriceCode(asset)
        dpToBc[dpKey] = asset
        val valuationDate =
            if (dateUtils.isToday(requestedDate)) {
                dateUtils.offsetDateString(requestedDate)
            } else {
                requestedDate
            }

        val datedBatch =
            datedBatches.getOrPut(currentBatch) {
                DatedBatch(
                    currentBatch,
                    valuationDate
                )
            }

        val searchKey = batch[datedBatch.batchId]?.let { it + delimiter + dpKey } ?: dpKey
        batch[currentBatch] = searchKey

        if (++count >= dataProviderConfig.getBatchSize()) {
            count = 0
            currentBatch++
        }
    }

    fun getAssets(batchId: Int): List<String> = batch[batchId]!!.split(delimiter)

    fun getBatchConfigs(id: Int): DatedBatch? = datedBatches[id]

    fun getAsset(dataProviderKey: String): Asset = dpToBc[dataProviderKey]!!

    companion object {
        /**
         * Helper to build an instance of this class based on the supplied arguments.
         *
         * @param priceRequest       Args
         * @param dataProviderConfig Who our provider is
         * @return This class with various keys cross-indexed for convenience
         */
        fun getInstance(
            priceRequest: PriceRequest,
            dataProviderConfig: DataProviderConfig,
            dateUtils: DateUtils = DateUtils()
        ): ProviderArguments {
            val providerArguments =
                ProviderArguments(
                    dataProviderConfig,
                    dateUtils
                )
            providerArguments.date = priceRequest.date

            split(priceRequest.assets).forEach { (_, assets) ->
                assets
                    .filter { it.resolvedAsset != null }
                    .forEach { asset ->
                        // Add the asset to the batch, bumping it if it exceeds
                        // the number of assets per request
                        providerArguments.batchAsset(
                            asset.resolvedAsset!!,
                            priceRequest.date
                        )
                    }
                // This looks unnecessary.
                providerArguments.bumpBatch()
            }

            return providerArguments
        }
    }
}