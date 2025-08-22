package com.beancounter.marketdata.assets

import com.beancounter.client.Assets
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataService
import jakarta.transaction.Transactional
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Asset CRUD functionality.
 */
@Service
@Import(
    DefaultEnricher::class,
    MarketDataService::class
)
@Transactional
@Suppress("TooManyFunctions") // AssetService has 12 functions, threshold is 11
class AssetService(
    private val enrichmentFactory: EnrichmentFactory,
    private val assetRepository: AssetRepository,
    private val marketService: MarketService,
    private val keyGenUtils: KeyGenUtils,
    private val assetFinder: AssetFinder
) : Assets {
    fun enrich(asset: Asset): Asset {
        val enricher = enrichmentFactory.getEnricher(asset.market)
        if (enricher.canEnrich(asset)) {
            val enriched =
                enricher.enrich(
                    asset.id,
                    asset.market,
                    AssetInput(
                        asset.market.code,
                        asset.code,
                        category = asset.category.uppercase()
                    )
                )
            assetRepository.save(enriched) // Hmm, not sure the Repo should be here
            return enriched
        }
        return asset
    }

    override fun handle(assetRequest: AssetRequest): AssetUpdateResponse {
        val assets =
            assetRequest.data
                .mapValues { (_, assetInput) -> create(assetInput) }
        return AssetUpdateResponse(assets)
    }

    override fun backFillEvents(assetId: String) {
        TODO("Not yet implemented")
    }

    override fun find(assetId: String): Asset = assetFinder.find(assetId)

    fun findOrCreate(assetInput: AssetInput): Asset {
        val localAsset = assetFinder.findLocally(assetInput)
        if (localAsset == null) {
            val market = marketService.getMarket(assetInput.market)
            val eAsset =
                enrichmentFactory.getEnricher(market).enrich(
                    id = keyGenUtils.format(UUID.randomUUID()),
                    market = market,
                    assetInput = assetInput
                )
            if (marketService.canPersist(market)) {
                return assetFinder.hydrateAsset(assetRepository.save(eAsset))
            }
        }
        if (localAsset == null) {
            throw BusinessException("Unable to resolve asset ${assetInput.code}")
        }
        return localAsset
    }

    fun purge() = assetRepository.deleteAll()

    fun resolveAssets(priceRequest: PriceRequest): PriceRequest {
        val assets = assetRepository.findAllById(priceRequest.assets.map { it.assetId })
        val resolvedAssets =
            priceRequest.assets.map { priceAsset ->
                val asset = assets.find { it.id == priceAsset.assetId }
                if (asset != null) {
                    priceAsset.resolvedAsset = assetFinder.hydrateAsset(asset)
                }
                priceAsset
            }
        return priceRequest.copy(assets = resolvedAssets)
    }

    private fun create(assetInput: AssetInput): Asset {
        val foundAsset = assetFinder.findLocally(assetInput)
        return if (foundAsset == null) {
            // Is the market supported?
            val market = marketService.getMarket(assetInput.market, false)
            // Fill in missing asset attributes
            val asset =
                enrichmentFactory
                    .getEnricher(market)
                    .enrich(
                        id = keyGenUtils.id,
                        market = market,
                        assetInput = assetInput
                    )
            assetFinder.hydrateAsset(assetRepository.save(asset))
        } else {
            foundAsset
        }
    }
}