package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.service.MarketDataService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.Optional
import java.util.UUID
import java.util.stream.Stream
import javax.transaction.Transactional

@Service
@Transactional
/**
 * Asset CRUD functionality.
 */
class AssetService internal constructor(
    private val enrichmentFactory: EnrichmentFactory,
    private val marketDataService: MarketDataService,
    private val assetRepository: AssetRepository,
    private val marketService: MarketService,
    private val keyGenUtils: KeyGenUtils,
) : com.beancounter.client.AssetService {

    private fun create(assetInput: AssetInput?): Asset {
        val foundAsset = findLocally(
            assetInput!!.market.toUpperCase(),
            assetInput.code.toUpperCase()
        )
        if (foundAsset == null) {
            // Is the market supported?
            val market = marketService.getMarket(assetInput.market, false)
            var defaultName: String? = null
            if (assetInput.name != null) {
                defaultName = assetInput.name!!.replace("\"", "")
            }

            // Enrich missing attributes
            var asset = enrichmentFactory.getEnricher(market).enrich(
                market,
                assetInput.code,
                defaultName
            )
            if (asset == null) {
                // User Defined Asset?
                asset = Asset(
                    keyGenUtils.format(UUID.randomUUID()),
                    assetInput.code.toUpperCase(),
                    defaultName,
                    "Equity",
                    market,
                    market.code,
                    null
                )
            } else {
                // Market Listed
                asset.market = market
                asset.id = keyGenUtils.format(UUID.randomUUID())
            }
            return hydrateAsset(assetRepository.save(asset))
        }
        return enrich(foundAsset)
    }

    override fun process(assetRequest: AssetRequest): AssetUpdateResponse {
        val assets: MutableMap<String, Asset> = HashMap()
        for (callerRef in assetRequest.data.keys) {
            val createdAsset = create(assetRequest.data[callerRef]!!)
            assets[callerRef] = createdAsset
        }
        return AssetUpdateResponse(assets)
    }

    @Async("priceExecutor")
    override fun backFillEvents(assetId: String) {
        marketDataService.backFill(find(assetId))
    }

    fun find(marketCode: String, code: String): Asset {
        var asset: Asset? = findLocally(marketCode, code)
        if (asset == null) {
            val market = marketService.getMarket(marketCode)
            asset = enrichmentFactory.getEnricher(market).enrich(market, code, null)
            if (asset == null) {
                throw BusinessException(String.format("No asset found for %s:%s", marketCode, code))
            }
            if (marketService.canPersist(market)) {
                asset = assetRepository.save(asset)
            }
        }
        return hydrateAsset(asset!!)
    }

    override fun find(assetId: String): Asset {
        val result: Optional<Asset> = assetRepository.findById(assetId).map { asset: Asset -> hydrateAsset(asset) }
        if (result.isPresent) {
            return result.get()
        }
        throw BusinessException(String.format("Asset %s not found", assetId))
    }

    fun findLocally(marketCode: String, code: String): Asset? {
        // Search Local
        log.trace("Search for {}/{}", marketCode, code)
        val optionalAsset = assetRepository.findByMarketCodeAndCode(marketCode.toUpperCase(), code.toUpperCase())
        return optionalAsset.map { asset: Asset -> hydrateAsset(asset) }.orElse(null)
    }

    fun enrich(assetId: String): Asset {
        return enrich(find(assetId))
    }

    private fun enrich(asset: Asset): Asset {
        val enricher = enrichmentFactory.getEnricher(asset.market)
        if (enricher.canEnrich(asset)) {
            val enriched = enricher.enrich(asset.market, asset.code, asset.name)
            if (enriched != null) {
                enriched.id = asset.id
                assetRepository.save(enriched)
                return enriched
            }
        }
        return asset
    }

    fun hydrateAsset(asset: Asset): Asset {
        asset.market = marketService.getMarket(asset.marketCode!!)
        return asset
    }

    fun findAllAssets(): Stream<Asset>? {
        return assetRepository.findAllAssets()
    }

    fun purge() {
        assetRepository.deleteAll()
    }

    companion object {
        private val log = LoggerFactory.getLogger(AssetService::class.java)
    }
}
