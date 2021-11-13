package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.key.KeyGenUtils
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataService
import org.slf4j.LoggerFactory
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
@Transactional
class AssetService internal constructor(
    private val enrichmentFactory: EnrichmentFactory,
    private val marketDataService: MarketDataService,
    private val assetRepository: AssetRepository,
    private val marketService: MarketService,
    private val assetHydrationService: AssetHydrationService,
    private val keyGenUtils: KeyGenUtils,
) : com.beancounter.client.AssetService {

    private fun create(assetInput: AssetInput): Asset {
        val foundAsset = findLocally(
            assetInput.market.uppercase(Locale.getDefault()),
            assetInput.code.uppercase(Locale.getDefault())
        )
        if (foundAsset == null) {
            // Is the market supported?
            val market = marketService.getMarket(assetInput.market, false)
            var defaultName: String? = null
            if (assetInput.name != null) {
                defaultName = assetInput.name!!.replace("\"", "")
            }
            val id = keyGenUtils.format(UUID.randomUUID())
            // Fill in missing asset attributes
            var asset = enrichmentFactory.getEnricher(market)
                .enrich(
                    id = id,
                    market = market,
                    code = assetInput.code,
                    defaultName = defaultName
                )
            if (asset == null) {
                // Cash or User Defined Asset
                asset = Asset(
                    id = id,
                    code = assetInput.code.uppercase(Locale.getDefault()),
                    name = defaultName,
                    category = assetInput.category,
                    market = market,
                    marketCode = market.code,
                    priceSymbol = assetInput.currency,
                )
            } else {
                // Market Listed
                asset.market = market
            }
            return assetHydrationService.hydrateAsset(assetRepository.save(asset))
        }
        return enrichmentFactory.enrich(foundAsset)
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
            asset = enrichmentFactory.getEnricher(market).enrich(
                keyGenUtils.format(UUID.randomUUID()),
                market,
                code,
                null
            )
            if (asset == null) {
                throw BusinessException(String.format("No asset found for %s:%s", marketCode, code))
            }
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
            code.uppercase(Locale.getDefault())
        )
        return optionalAsset.map { asset: Asset -> assetHydrationService.hydrateAsset(asset) }.orElse(null)
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
