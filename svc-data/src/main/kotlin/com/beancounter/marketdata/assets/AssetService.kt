package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.Locale
import java.util.Optional
import java.util.UUID
import java.util.stream.Stream
import javax.transaction.Transactional

/**
 * Asset CRUD functionality.
 */
@Service
@Import(
    DefaultEnricher::class,
    MarketDataService::class,
)
@Transactional
class AssetService internal constructor(
    private val enrichmentFactory: EnrichmentFactory,
    private val marketDataService: MarketDataService,
    private val assetRepository: AssetRepository,
    private val marketService: MarketService,
    private val assetHydrationService: AssetHydrationService,
) : com.beancounter.client.AssetService {
    private val keyGenUtils = KeyGenUtils()

    fun enrich(asset: Asset): Asset {
        val enricher = enrichmentFactory.getEnricher(asset.market)
        if (enricher.canEnrich(asset)) {
            val enriched = enricher.enrich(
                asset.id,
                asset.market,
                AssetInput(asset.market.code, asset.code, category = asset.category.uppercase()),
            )
            assetRepository.save(enriched) // Hmm, not sure the Repo should be here
            return enriched
        }
        return asset
    }

    private fun create(assetInput: AssetInput): Asset {
        val foundAsset = findLocally(
            assetInput.market.uppercase(Locale.getDefault()),
            assetInput.code.uppercase(Locale.getDefault()),
        )
        return if (foundAsset == null) {
            // Is the market supported?
            val market = marketService.getMarket(assetInput.market, false)
            // Fill in missing asset attributes
            val asset = enrichmentFactory.getEnricher(market)
                .enrich(
                    id = keyGenUtils.id,
                    market = market,
                    assetInput = assetInput,
                )
            assetHydrationService.hydrateAsset(assetRepository.save(asset))
        } else {
            assetHydrationService.hydrateAsset(foundAsset)
        }
    }

    override fun handle(assetRequest: AssetRequest): AssetUpdateResponse {
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
            asset = enrichmentFactory.getEnricher(market).enrich(
                id = keyGenUtils.format(UUID.randomUUID()),
                market = market,
                assetInput = AssetInput(market = marketCode, code = code),
            )
            if (marketService.canPersist(market)) {
                asset = assetRepository.save(asset)
            }
        }
        return assetHydrationService.hydrateAsset(asset!!)
    }

    override fun find(assetId: String): Asset {
        val result: Optional<Asset> =
            assetRepository.findById(assetId).map { asset: Asset -> assetHydrationService.hydrateAsset(asset) }
        if (result.isPresent) {
            return result.get()
        }
        throw BusinessException("Asset $assetId not found")
    }

    fun findLocally(marketCode: String, code: String): Asset? {
        // Search Local
        log.trace("Search for {}/{}", marketCode, code)
        val optionalAsset = assetRepository.findByMarketCodeAndCode(
            marketCode.uppercase(Locale.getDefault()),
            code.uppercase(Locale.getDefault()),
        )
        return optionalAsset.map { asset: Asset ->
            assetHydrationService.hydrateAsset(asset)
        }.orElse(null)
    }

    fun findAllAssets(): Stream<Asset> {
        return assetRepository.findAllAssets()
    }

    fun purge() = assetRepository.deleteAll()

    companion object {
        private val log = LoggerFactory.getLogger(AssetService::class.java)
    }
}
