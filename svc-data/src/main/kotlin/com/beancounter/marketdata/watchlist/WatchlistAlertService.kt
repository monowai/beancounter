package com.beancounter.marketdata.watchlist

import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TargetDirection
import com.beancounter.common.model.WatchlistItem
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.PriceService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

/**
 * Watchlist target alerts. [checkTargets] runs after the scheduled price refresh: each armed
 * item (a target not yet reached) is compared with its asset's latest stored close dated on
 * or after the target was set. The stored target is on the share basis of its targetOn date
 * and the raw close on its own, so the target is rebased across splits between the two. A
 * crossing in the item's fixed direction stamps the item's targetHitOn, only while it is still
 * unstamped, and writes a [WatchlistAlert], so each target fires once even when checks overlap.
 *
 * Read paths are owner-scoped; another user's alert is reported as not found.
 */
@Service
class WatchlistAlertService(
    private val itemRepository: WatchlistItemRepository,
    private val alertRepository: WatchlistAlertRepository,
    private val priceService: PriceService,
    private val dateUtils: DateUtils,
    transactionManager: PlatformTransactionManager
) {
    // One short transaction per item: a failure rolls back only that item, never the sweep.
    private val perItem =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    /**
     * Fire every armed target its latest close has reached. Deliberately not transactional:
     * one item's failure is logged and the rest are still checked. Returns the alerts raised.
     */
    fun checkTargets(): Int {
        val today = dateUtils.date
        val armed = itemRepository.findByTargetPriceIsNotNullAndTargetHitOnIsNull()
        val closes =
            priceService
                .getLatestMarketData(armed.map { it.asset }.distinctBy { it.id }, today)
                .filterValues { it.close.signum() > 0 }
        val candidates =
            armed.mapNotNull { item ->
                closes[item.asset.id]
                    ?.takeIf { !it.priceDate.isBefore(targetOn(item)) }
                    ?.let { item to it }
            }
        val factors =
            splitFactors(
                candidates.map { (item, close) -> item.asset to listOf(targetOn(item), close.priceDate) },
                today
            )
        var fired = 0
        var failed = 0
        candidates.forEach { (item, close) ->
            try {
                if (fire(item, close, factors.getValue(item.asset.id))) fired++
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception
            ) {
                failed++
                log.warn("Watchlist target check failed for item {}: {}", item.id, e.message)
            }
        }
        log.info(
            "Watchlist target check: armed {}, priced since target {}, fired {}, failed {}",
            armed.size,
            candidates.size,
            fired,
            failed
        )
        return fired
    }

    /**
     * The caller's alerts, newest first, with prices on today's share basis.
     */
    @Transactional(readOnly = true)
    fun alerts(owner: SystemUser): List<WatchlistAlertView> {
        val alerts = alertRepository.findByOwnerIdOrderByCreatedAtDesc(owner.id)
        val factors = splitFactors(alerts.map { it.asset to listOf(it.priceDate) }, dateUtils.date)
        return alerts.map { alert ->
            val factor = factors.getValue(alert.asset.id).getValue(alert.priceDate)
            WatchlistAlertView(
                id = alert.id,
                itemId = alert.item.id,
                asset = alert.asset,
                currency = alert.asset.watchlistCurrency(),
                targetPrice = rebase(alert.targetPrice, factor),
                direction = alert.direction,
                close = rebase(alert.close, factor),
                priceDate = alert.priceDate,
                createdAt = alert.createdAt
            )
        }
    }

    /**
     * Dismiss one of the caller's alerts; returns the alerts that remain.
     */
    @Transactional
    fun deleteAlert(
        owner: SystemUser,
        alertId: String
    ): List<WatchlistAlertView> {
        val alert =
            alertRepository
                .findByIdAndOwnerId(alertId, owner.id)
                .orElseThrow { NotFoundException("Watchlist alert not found: $alertId") }
        alertRepository.delete(alert)
        return alerts(owner)
    }

    private fun fire(
        item: WatchlistItem,
        close: MarketData,
        factors: Map<LocalDate, BigDecimal>
    ): Boolean =
        perItem.execute {
            // Re-read inside the transaction: the target may have been edited since the sweep began.
            val found = itemRepository.findById(item.id)
            if (found.isEmpty) return@execute false
            val current = found.get()
            val unchanged =
                current.targetHitOn == null &&
                    current.targetOn == item.targetOn &&
                    current.targetPrice?.compareTo(item.targetPrice) == 0
            if (!unchanged) return@execute false
            val stored = requireNotNull(current.targetPrice)
            // Undo the splits after targetOn, re-apply those after the close: the close's basis.
            val target =
                stored
                    .multiply(factors.getValue(close.priceDate))
                    .divide(factors.getValue(targetOn(current)), PRICE_SCALE, RoundingMode.HALF_UP)
            if (current.directionUnverified) {
                // V44's backfill compared prices on different share bases: re-derive the side
                // from this close. A target never fires on the close its side was taken from.
                current.targetDirection = TargetDirection.of(target, close.close)
                current.directionUnverified = false
                itemRepository.save(current)
                return@execute false
            }
            val direction = requireNotNull(current.targetDirection) { "Target without direction: ${item.id}" }
            if (!direction.reached(close.close, target)) return@execute false
            // Conditional stamp: an overlapping check that got there first raises no second alert.
            if (itemRepository.markHit(current.id, targetOn(current), stored, close.priceDate) == 0) {
                return@execute false
            }
            alertRepository.save(
                WatchlistAlert(
                    owner = current.owner,
                    item = current,
                    asset = current.asset,
                    targetPrice = target,
                    direction = direction,
                    close = close.close,
                    priceDate = close.priceDate,
                    createdAt = Instant.now()
                )
            )
            true
        } == true

    private fun targetOn(item: WatchlistItem): LocalDate =
        requireNotNull(item.targetOn) { "Target without entry date: ${item.id}" }

    /**
     * Split factors to today, per asset and date, batched across every asset in one read
     * (see [PriceService.splitFactorsAfter]).
     */
    private fun splitFactors(
        datesByAsset: List<Pair<Asset, List<LocalDate>>>,
        today: LocalDate
    ): Map<String, Map<LocalDate, BigDecimal>> =
        priceService.splitFactorsAfter(
            datesByAsset
                .groupBy { it.first.id }
                .values
                .associate { pairs -> pairs.first().first to pairs.flatMap { it.second } },
            today
        )

    companion object {
        private val log = LoggerFactory.getLogger(WatchlistAlertService::class.java)
    }
}