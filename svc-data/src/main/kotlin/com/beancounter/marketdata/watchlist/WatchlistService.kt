package com.beancounter.marketdata.watchlist

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Watchlist
import com.beancounter.common.model.WatchlistItem
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.PriceService
import jakarta.transaction.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

/**
 * Owner-scoped watchlists. A watchlist that is missing or belongs to someone else is
 * reported as not found, so existence is never leaked across users.
 *
 * Adding without a price goes through the provider-aware price path. Read paths attach the
 * latest stored close (DB only; the scheduled refresh keeps it fresh) and compute every
 * change server side. Percentages are fractions at scale 6, matching MarketData.changePercent.
 */
@Service
@Transactional
class WatchlistService(
    private val watchlistRepository: WatchlistRepository,
    private val itemRepository: WatchlistItemRepository,
    private val assetFinder: AssetFinder,
    private val priceService: PriceService,
    private val marketDataService: MarketDataService,
    private val dateUtils: DateUtils
) {
    fun list(owner: SystemUser): List<WatchlistSummary> {
        val counts = itemRepository.countByOwnerId(owner.id).associate { it.watchlistId to it.itemCount.toInt() }
        return watchlistRepository.findByOwnerIdOrderByName(owner.id).map {
            WatchlistSummary(it.id, it.code, it.name, counts[it.id] ?: 0)
        }
    }

    fun create(
        owner: SystemUser,
        request: CreateWatchlistRequest
    ): WatchlistView {
        val code = normaliseCode(request.code)
        assertCodeFree(owner, code)
        val watchlist =
            watchlistRepository.save(
                Watchlist(
                    owner = owner,
                    code = code,
                    name = requireText(request.name, "name"),
                    createdAt = dateUtils.date
                )
            )
        return view(watchlist, emptyList())
    }

    fun get(
        owner: SystemUser,
        id: String
    ): WatchlistView {
        val watchlist = getOrThrow(owner, id)
        return view(watchlist, itemRepository.findByWatchlistIdOrderByAssetCode(watchlist.id))
    }

    fun update(
        owner: SystemUser,
        id: String,
        request: UpdateWatchlistRequest
    ): WatchlistView {
        val watchlist = getOrThrow(owner, id)
        request.code?.let { requested ->
            val code = normaliseCode(requested)
            if (code != watchlist.code) {
                assertCodeFree(owner, code)
                watchlist.code = code
            }
        }
        request.name?.let { watchlist.name = requireText(it, "name") }
        val saved = watchlistRepository.save(watchlist)
        return view(saved, itemRepository.findByWatchlistIdOrderByAssetCode(saved.id))
    }

    fun delete(
        owner: SystemUser,
        id: String
    ) {
        val watchlist = getOrThrow(owner, id)
        itemRepository.deleteByWatchlistId(watchlist.id)
        watchlistRepository.delete(watchlist)
    }

    fun addItem(
        owner: SystemUser,
        id: String,
        request: AddWatchlistItemRequest
    ): WatchlistItemView {
        val watchlist = getOrThrow(owner, id)
        val asset = visibleAsset(owner, request.assetId)
        if (itemRepository.existsByWatchlistIdAndAssetId(watchlist.id, asset.id)) {
            throw DataIntegrityViolationException("Asset ${asset.id} is already on watchlist ${watchlist.code}")
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
            latest = priceService.enrichedCopy(fetched)
            addedPrice = fetched.close
        } else {
            latest = latestPrices(listOf(asset))[asset.id]
            addedPrice = requirePositive(requested, "addedPrice")
        }
        val targetPrice = request.targetPrice?.let { requirePositive(it, "targetPrice") }
        val item =
            itemRepository.save(
                WatchlistItem(
                    watchlist = watchlist,
                    asset = asset,
                    addedPrice = addedPrice,
                    addedOn = dateUtils.date,
                    targetPrice = targetPrice,
                    targetOn = targetPrice?.let { dateUtils.date }
                )
            )
        return itemView(item, latest)
    }

    fun updateItem(
        owner: SystemUser,
        id: String,
        itemId: String,
        request: UpdateWatchlistItemRequest
    ): WatchlistItemView {
        val item = itemOrThrow(owner, id, itemId)
        if (request.clearTarget && request.targetPrice != null) {
            throw BusinessException("Send either targetPrice or clearTarget, not both")
        }
        // Callers send prices on today's share basis. addedPrice stays stored on the addedOn
        // basis, so undo any split since then; a new target is simply dated today.
        request.addedPrice?.let { entered ->
            val factor = splitFactors(item.asset, listOf(item.addedOn)).getValue(item.addedOn)
            item.addedPrice = requirePositive(entered, "addedPrice").multiply(factor)
        }
        request.targetPrice?.let {
            item.targetPrice = requirePositive(it, "targetPrice")
            item.targetOn = dateUtils.date
        }
        if (request.clearTarget) {
            item.targetPrice = null
            item.targetOn = null
        }
        val saved = itemRepository.save(item)
        return itemView(saved, latestPrices(listOf(saved.asset))[saved.asset.id])
    }

    fun deleteItem(
        owner: SystemUser,
        id: String,
        itemId: String
    ) {
        itemRepository.delete(itemOrThrow(owner, id, itemId))
    }

    /**
     * Offboarding: remove every watchlist the owner has, items first. Idempotent.
     */
    fun deleteByOwner(ownerId: String): Int {
        itemRepository.deleteByOwnerId(ownerId)
        return watchlistRepository.deleteByOwnerId(ownerId)
    }

    private fun getOrThrow(
        owner: SystemUser,
        id: String
    ): Watchlist =
        watchlistRepository
            .findByIdAndOwnerId(id, owner.id)
            .orElseThrow { NotFoundException("Watchlist not found: $id") }

    private fun itemOrThrow(
        owner: SystemUser,
        id: String,
        itemId: String
    ): WatchlistItem {
        val watchlist = getOrThrow(owner, id)
        return itemRepository
            .findByIdAndWatchlistId(itemId, watchlist.id)
            .orElseThrow { NotFoundException("Watchlist item not found: $itemId") }
    }

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

    private fun assertCodeFree(
        owner: SystemUser,
        code: String
    ) {
        if (watchlistRepository.existsByOwnerIdAndCode(owner.id, code)) {
            throw DataIntegrityViolationException("Watchlist code $code already exists")
        }
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

    /**
     * Latest close on or before today, ignoring rows without a positive close.
     */
    private fun latestPrices(assets: Collection<Asset>): Map<String, MarketData> =
        priceService
            .getLatestMarketData(assets, dateUtils.date)
            .filterValues { it.close.signum() > 0 }

    private fun view(
        watchlist: Watchlist,
        items: List<WatchlistItem>
    ): WatchlistView {
        val prices = latestPrices(items.map { it.asset })
        return WatchlistView(
            id = watchlist.id,
            code = watchlist.code,
            name = watchlist.name,
            createdAt = watchlist.createdAt,
            items = items.map { itemView(it, prices[it.asset.id]) }
        )
    }

    private fun itemView(
        item: WatchlistItem,
        latest: MarketData?
    ): WatchlistItemView {
        val targetOn = item.targetOn
        val factors = splitFactors(item.asset, listOfNotNull(item.addedOn, targetOn))
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
            currency = item.asset.accountingType?.currency ?: item.asset.market.currency,
            price = price,
            priceDate = latest?.priceDate,
            changeOnDay = latest?.change,
            changeOnDayPercent = latest?.changePercent,
            changeSinceAdded = sinceAdded,
            changeSinceAddedPercent = sinceAdded?.let { fraction(it, addedPrice) },
            toTarget = if (price == null || target == null) null else fraction(target.subtract(price), price)
        )
    }

    /**
     * Stored prices sit on the share basis of the day they were entered, while the latest close
     * is on today's basis (SPLITS.md: the DB keeps raw OHLC). Factors per entry date come from
     * the stored split column.
     */
    private fun splitFactors(
        asset: Asset,
        dates: List<LocalDate>
    ): Map<LocalDate, BigDecimal> = priceService.splitFactorsAfter(asset.id, dates, dateUtils.date)

    private fun rebase(
        price: BigDecimal,
        factor: BigDecimal
    ): BigDecimal =
        if (factor.compareTo(BigDecimal.ONE) ==
            0
        ) {
            price
        } else {
            price.divide(factor, PERCENT_SCALE, RoundingMode.HALF_UP)
        }

    private fun fraction(
        numerator: BigDecimal,
        denominator: BigDecimal
    ): BigDecimal = numerator.divide(denominator, PERCENT_SCALE, RoundingMode.HALF_UP)

    private fun normaliseCode(code: String): String = requireText(code, "code").uppercase(Locale.getDefault())

    private fun requireText(
        value: String,
        field: String
    ): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw BusinessException("Watchlist $field is required")
        }
        if (trimmed.length > MAX_TEXT_LENGTH) {
            throw BusinessException("Watchlist $field must be at most $MAX_TEXT_LENGTH characters")
        }
        return trimmed
    }

    private fun requirePositive(
        value: BigDecimal,
        field: String
    ): BigDecimal {
        if (value.signum() <= 0) {
            throw BusinessException("$field must be greater than zero")
        }
        return value
    }

    companion object {
        private const val PERCENT_SCALE = 6

        // watchlist.code / watchlist.name are VARCHAR(255).
        private const val MAX_TEXT_LENGTH = 255
    }
}