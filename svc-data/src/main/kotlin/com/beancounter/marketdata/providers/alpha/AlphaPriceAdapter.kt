package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.exception.SystemException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.MathUtils.Companion.multiplyAbs
import com.beancounter.marketdata.providers.ProviderArguments
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.math.BigDecimal

/**
 * Convert Alpha MarketData to BeanCounter MarketData
 */
@Service
class AlphaPriceAdapter(
    val alphaConfig: AlphaConfig,
    private val corporateEventEnricher: AlphaCorporateEventEnricher
) {
    operator fun get(
        providerArguments: ProviderArguments,
        batchId: Int,
        response: String?,
        currentMode: Boolean = true
    ): Collection<MarketData> {
        val results: MutableCollection<MarketData> = ArrayList()
        try {
            val assets = providerArguments.getAssets(batchId)
            for (dpAsset in assets) {
                val asset = providerArguments.getAsset(dpAsset)
                setPriceResponse(
                    asset,
                    response,
                    results,
                    providerArguments,
                    currentMode
                )
            }
        } catch (e: IOException) {
            throw SystemException("Failed to process Alpha price data", e)
        }
        return results
    }

    private fun setPriceResponse(
        asset: Asset?,
        response: String?,
        results: MutableCollection<MarketData>,
        providerArguments: ProviderArguments,
        currentMode: Boolean
    ) {
        if (isMdResponse(
                asset,
                response
            )
        ) {
            setPriceResponse(
                response,
                asset,
                results,
                providerArguments,
                currentMode
            )
        } else {
            results.add(
                getDefault(
                    asset,
                    providerArguments
                )
            )
        }
    }

    private fun setPriceResponse(
        response: String?,
        asset: Asset?,
        results: MutableCollection<MarketData>,
        providerArguments: ProviderArguments,
        currentMode: Boolean
    ) {
        val priceResponse =
            alphaConfig.getObjectMapper().readValue(
                response,
                PriceResponse::class.java
            )
        if (priceResponse != null && priceResponse.data.isNotEmpty()) {
            for (marketData in priceResponse.data) {
                marketData.asset = asset!! // Return BC view of the asset, not MarketProviders
                normalise(
                    asset.market,
                    marketData
                )
                // Enrich with corporate events (splits/dividends) from adjusted time series.
                // Only for current-mode requests; historical backfill doesn't need enrichment.
                if (currentMode) {
                    corporateEventEnricher.enrich(marketData)
                }
                log.trace(
                    "Valued {} ",
                    asset.name
                )
                results.add(marketData)
            }
        } else {
            results.add(
                getDefault(
                    asset,
                    providerArguments
                )
            )
        }
    }

    private fun normalise(
        market: Market,
        marketData: MarketData
    ) {
        if (market.multiplier.compareTo(BigDecimal.ONE) != 0) {
            marketData.close =
                multiplyAbs(
                    marketData.close,
                    market.multiplier,
                    4
                )
            marketData.open =
                multiplyAbs(
                    marketData.open,
                    market.multiplier,
                    4
                )
            marketData.high =
                multiplyAbs(
                    marketData.high,
                    market.multiplier,
                    4
                )
            marketData.low =
                multiplyAbs(
                    marketData.low,
                    market.multiplier,
                    4
                )
            marketData.previousClose =
                multiplyAbs(
                    marketData.previousClose,
                    market.multiplier,
                    4
                )
            marketData.change =
                multiplyAbs(
                    marketData.change,
                    market.multiplier,
                    4
                )
        }
    }

    private fun isMdResponse(
        asset: Asset?,
        result: String?
    ): Boolean {
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
            val resultMessage = alphaConfig.getObjectMapper().readTree(result)
            log.debug(
                "API returned [{}] for {}",
                resultMessage[field],
                asset
            )
            return false
        }
        return true
    }

    private fun getDefault(
        asset: Asset?,
        providerArguments: ProviderArguments
    ): MarketData {
        var date = providerArguments.getBatchConfigs(0)?.date

        if (date == null) {
            date = providerArguments.date
        }
        val priceDate = alphaConfig.dateUtils.getFormattedDate(date)

        return MarketData(
            asset = asset!!,
            priceDate = priceDate
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(AlphaPriceAdapter::class.java)
    }
}