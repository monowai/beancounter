package com.beancounter.marketdata.watchlist

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TargetDirection
import com.beancounter.common.model.WatchlistItem
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetFinder
import jakarta.transaction.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The caller's watchlist: one per user, made of the [WatchlistItem]s they own. An item that is
 * missing or belongs to someone else is reported as not found, so existence never leaks.
 *
 * Prices, and every change computed from them, come from [WatchlistQuotes].
 */
@Service
@Transactional
class WatchlistService(
    private val itemRepository: WatchlistItemRepository,
    private val alertRepository: WatchlistAlertRepository,
    private val assetFinder: AssetFinder,
    private val quotes: WatchlistQuotes,
    private val dateUtils: DateUtils
) {
    // No transaction: the live-quote call must not hold a connection while the provider answers.
    // Each read below runs in its own repository transaction and items load their asset eagerly.
    @Transactional(Transactional.TxType.SUPPORTS)
    fun get(owner: SystemUser): WatchlistView = quotes.view(itemRepository.findByOwnerIdOrderByAssetCode(owner.id))

    // No transaction, as for get: the live quote calls the provider. The write is the single save below.
    @Transactional(Transactional.TxType.SUPPORTS)
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
            latest = quotes.priceToAdd(asset)
            addedPrice = latest.close
        } else {
            latest = quotes.live(asset)
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
        return quotes.itemView(saved, latest)
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

    // No transaction, as for get: the live quote calls the provider. The write is the single save below.
    @Transactional(Transactional.TxType.SUPPORTS)
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
        val addedFactor = quotes.addedFactor(item)
        request.addedPrice?.let { entered ->
            item.addedPrice =
                requirePositive(entered, "addedPrice")
                    .multiply(addedFactor)
                    .setScale(PRICE_SCALE, RoundingMode.HALF_UP)
        }
        val latest = quotes.live(item.asset)
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
        return quotes.itemView(saved, latest)
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