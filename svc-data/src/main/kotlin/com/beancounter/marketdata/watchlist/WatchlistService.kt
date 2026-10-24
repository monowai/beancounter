package com.beancounter.marketdata.watchlist

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TargetDirection
import com.beancounter.common.model.WatchlistItem
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.providers.LiveQuoteService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.PriceService
import jakarta.transaction.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * The caller's watchlist: one per user, made of the [WatchlistItem]s they own. An item that is
 * missing or belongs to someone else is reported as not found, so existence never leaks.
 *
 * Adding without a price goes through the provider-aware price path. Read paths attach the
 * latest stored close (DB only; the scheduled refresh keeps it fresh) and compute every
 * change server side. Percentages are fractions at scale 6, matching MarketData.changePercent.
 */
@Service
@Transactional
class WatchlistService(
    private val itemRepository: WatchlistItemRepository,
    private val alertRepository: WatchlistAlertRepository,
    private val assetFinder: AssetFinder,
    private val priceService: PriceService,
    private val marketDataService: MarketDataService,
    private val liveQuoteService: LiveQuoteService,
    private val dateUtils: DateUtils
) {
    // No transaction: the live-quote call must not hold a connection while the provider answers.
    // Each read below runs in its own repository transaction and items load their asset eagerly.
    @Transactional(Transactional.TxType.SUPPORTS)
    fun get(owner: SystemUser): WatchlistView = view(itemRepository.findByOwnerIdOrderByAssetCode(owner.id))

    fun addItem(
        owner: SystemUser,
        request: AddWatchlistItemRequest
    ): WatchlistItemView {
        val asset = visibleAsset(owner, request.assetId)
        if (itemRepository.existsByOwnerIdAndAssetId(owner.id, asset.id)) {
            throw DataIntegrityViolationException("Asset ${asset.id} is already on the watchlist")
        }
        val requested = request.addedPrice
        val latest: MarketData?
        val addedPrice: BigDecimal
        if (requested == null) {
            val fetched =
                fetchPrice(asset)
                    ?: throw BusinessException("No price is available for asset ${asset.id}; supply addedPrice")
            // The fetched row is persisted (and enriched) asynchronously; enrich a copy the
            // same way so this response shows the day change a later GET will read.
            // Enrichment can rebase an unadjusted ex-date close; stamp that close so the stored
            // addedPrice and the persisted latest row share a basis.
            latest = priceService.enrichedCopy(fetched)
            addedPrice = latest.close
        } else {
            latest = livePrices(listOf(asset))[asset.id]
            addedPrice = requirePositive(requested, "addedPrice")
        }
        val targetPrice = request.targetPrice?.let { requirePositive(it, "targetPrice") }
        val item =
            WatchlistItem(
                owner = owner,
                asset = asset,
                addedPrice = addedPrice,
                addedOn = dateUtils.date
            )
        // addedPrice was just entered, so it is already on today's basis.
        targetPrice?.let { setTarget(item, it, latest?.close ?: addedPrice) }
        val saved = itemRepository.save(item)
        return itemView(saved, latest, splitFactors(listOf(saved)))
    }

    /**
     * Arm a target entered on today's basis. Its direction is fixed against [reference], the
     * price the caller sees now: ABOVE when the target is at or above it, else BELOW.
     */
    private fun setTarget(
        item: WatchlistItem,
        target: BigDecimal,
        reference: BigDecimal
    ) {
        item.targetPrice = target
        item.targetOn = dateUtils.date
        item.targetDirection = TargetDirection.of(target, reference)
        item.targetHitOn = null
        item.directionUnverified = false
    }

    fun updateItem(
        owner: SystemUser,
        itemId: String,
        request: UpdateWatchlistItemRequest
    ): WatchlistItemView {
        val item = itemOrThrow(owner, itemId)
        if (request.clearTarget && request.targetPrice != null) {
            throw BusinessException("Send either targetPrice or clearTarget, not both")
        }
        // Callers send prices on today's share basis. addedPrice stays stored on the addedOn
        // basis, so undo any split since then; a new target is simply dated today.
        val addedFactor = splitFactors(listOf(item)).getValue(item.id).getValue(item.addedOn)
        request.addedPrice?.let { entered ->
            item.addedPrice =
                requirePositive(entered, "addedPrice")
                    .multiply(addedFactor)
                    .setScale(PRICE_SCALE, RoundingMode.HALF_UP)
        }
        val latest = livePrices(listOf(item.asset))[item.asset.id]
        request.targetPrice?.let {
            setTarget(item, requirePositive(it, "targetPrice"), latest?.close ?: rebase(item.addedPrice, addedFactor))
        }
        if (request.clearTarget) {
            item.targetPrice = null
            item.targetOn = null
            item.targetDirection = null
            item.targetHitOn = null
            item.directionUnverified = false
        }
        val saved = itemRepository.save(item)
        return itemView(saved, latest, splitFactors(listOf(saved)))
    }

    fun deleteItem(
        owner: SystemUser,
        itemId: String
    ) {
        val item = itemOrThrow(owner, itemId)
        alertRepository.deleteByItemId(item.id)
        itemRepository.delete(item)
    }

    /**
     * Offboarding: remove the owner's watchlist and its alerts. Idempotent.
     */
    fun deleteByOwner(ownerId: String): Int {
        alertRepository.deleteByOwnerId(ownerId)
        return itemRepository.deleteByOwnerId(ownerId)
    }

    private fun itemOrThrow(
        owner: SystemUser,
        itemId: String
    ): WatchlistItem =
        itemRepository
            .findByIdAndOwnerId(itemId, owner.id)
            .orElseThrow { NotFoundException("Watchlist item not found: $itemId") }

    /**
     * Public assets are visible to everyone; a private asset only to its owner. Anything else
     * is reported as not found.
     */
    private fun visibleAsset(
        owner: SystemUser,
        assetId: String
    ): Asset {
        val asset = assetFinder.find(assetId)
        val assetOwner = asset.systemUser
        if (assetOwner != null && assetOwner.id != owner.id) {
            throw NotFoundException("Asset not found: $assetId")
        }
        return asset
    }

    /**
     * Provider-aware price for today: resolves each provider's market date, reads the DB and
     * fetches-and-stores on a miss. Used only when adding without a caller price, so a
     * never-priced asset picked from search can still be stamped.
     */
    private fun fetchPrice(asset: Asset): MarketData? =
        marketDataService
            .getPriceResponse(PriceRequest.of(asset, TODAY))
            .data
            .firstOrNull { it.close.signum() > 0 }

    /** Live (delayed) prices over the stored closes: a watchlist is read for the price now. */
    private fun livePrices(assets: Collection<Asset>): Map<String, MarketData> =
        liveQuoteService.livePrices(assets, latestPrices(assets))

    /**
     * Latest close on or before today, ignoring rows without a positive close.
     */
    private fun latestPrices(assets: Collection<Asset>): Map<String, MarketData> =
        priceService
            .getLatestMarketData(assets, dateUtils.date)
            .filterValues { it.close.signum() > 0 }

    private fun view(items: List<WatchlistItem>): WatchlistView {
        val prices = livePrices(items.map { it.asset })
        return WatchlistView(
            items =
                splitFactors(items).let { factors ->
                    items.map { itemView(it, prices[it.asset.id], factors) }
                }
        )
    }

    private fun itemView(
        item: WatchlistItem,
        latest: MarketData?,
        factorsByItem: Map<String, Map<LocalDate, BigDecimal>>
    ): WatchlistItemView {
        val targetOn = item.targetOn
        val factors = factorsByItem.getValue(item.id)
        val addedPrice = rebase(item.addedPrice, factors.getValue(item.addedOn))
        val target = item.targetPrice?.let { rebase(it, factors.getValue(requireNotNull(targetOn))) }
        val price = latest?.close
        val sinceAdded = price?.subtract(addedPrice)
        return WatchlistItemView(
            id = item.id,
            asset = item.asset,
            addedPrice = addedPrice,
            addedOn = item.addedOn,
            targetPrice = target,
            currency = item.asset.watchlistCurrency(),
            price = price,
            priceDate = latest?.priceDate,
            changeOnDay = latest?.change,
            changeOnDayPercent = latest?.changePercent,
            changeSinceAdded = sinceAdded,
            changeSinceAddedPercent = sinceAdded?.let { fraction(it, addedPrice) },
            toTarget = if (price == null || target == null) null else fraction(target.subtract(price), price),
            targetHitOn = item.targetHitOn
        )
    }

    /**
     * Stored prices sit on the share basis of the day they were entered, while the latest close
     * is on today's basis (SPLITS.md: the DB keeps raw OHLC). Factors per item and entry date
     * come from the stored split column.
     */
    private fun splitFactors(items: List<WatchlistItem>): Map<String, Map<LocalDate, BigDecimal>> {
        // Batched across every asset on the watchlist (DATA-6G: no per-item history reads).
        val datesByAsset =
            items
                .groupBy { it.asset.id }
                .values
                .associate { assetItems ->
                    assetItems.first().asset to assetItems.flatMap { listOfNotNull(it.addedOn, it.targetOn) }
                }
        val byAsset = priceService.splitFactorsAfter(datesByAsset, dateUtils.date)
        return items.associate { it.id to byAsset.getValue(it.asset.id) }
    }

    private fun fraction(
        numerator: BigDecimal,
        denominator: BigDecimal
    ): BigDecimal = numerator.divide(denominator, PRICE_SCALE, RoundingMode.HALF_UP)

    private fun requirePositive(
        value: BigDecimal,
        field: String
    ): BigDecimal {
        if (value.signum() <= 0) {
            throw BusinessException("$field must be greater than zero")
        }
        return value
    }
}