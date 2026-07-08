package com.beancounter.marketdata.trn

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.broker.BrokerService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Service for broker-related transaction operations including holdings aggregation and transfers.
 */
@Service
@Transactional
class TrnBrokerService(
    private val trnRepository: TrnRepository,
    private val brokerService: BrokerService,
    private val systemUserService: SystemUserService,
    private val assetFinder: AssetFinder,
    private val trnMigrator: TrnMigrator,
    private val trnService: TrnService,
    private val portfolioService: PortfolioService
) {
    private val log = LoggerFactory.getLogger(TrnBrokerService::class.java)

    companion object {
        const val NO_BROKER = "NO_BROKER"
    }

    /**
     * Find all transactions for a broker up to a given date for position building.
     * Includes all transaction types (BUY, SELL, SPLIT, DIVI, etc.) needed for
     * accurate position calculation with split adjustments.
     *
     * @param brokerId broker ID, or "NO_BROKER" for unassigned transactions
     * @param tradeDate transactions up to this date
     */
    fun findForBroker(
        brokerId: String,
        tradeDate: java.time.LocalDate
    ): Collection<Trn> {
        val user = systemUserService.getOrThrow()

        val results =
            if (brokerId == NO_BROKER) {
                trnRepository.findAllWithNoBrokerForPositions(
                    user,
                    tradeDate,
                    TrnStatus.SETTLED
                )
            } else {
                val broker =
                    brokerService.findById(brokerId, user).orElseThrow {
                        NotFoundException("Broker not found: $brokerId")
                    }
                // Splits carry no broker; merge them so the Accumulator applies the
                // split adjustment to the broker-scoped position.
                trnRepository.findAllByBrokerIdForPositions(
                    broker.id,
                    user,
                    tradeDate,
                    TrnStatus.SETTLED
                ) +
                    trnRepository.findBrokerSplits(
                        broker.id,
                        user,
                        tradeDate,
                        TrnStatus.SETTLED
                    )
            }
        log.trace("trns: ${results.size}, broker: $brokerId, asAt: $tradeDate")
        return postProcess(results.toList())
    }

    /**
     * Transfer all transactions from one broker to another.
     * @param fromBrokerId Source broker ID
     * @param toBrokerId Target broker ID
     * @return Number of transactions transferred
     */
    fun transferBroker(
        fromBrokerId: String,
        toBrokerId: String
    ): Long {
        val user = systemUserService.getOrThrow()
        val toBroker =
            brokerService.findById(toBrokerId, user).orElseThrow {
                NotFoundException("Target broker not found: $toBrokerId")
            }

        val transactions = trnRepository.findAllByBrokerId(fromBrokerId, user)
        if (transactions.isEmpty()) {
            log.info("No transactions to transfer from broker $fromBrokerId")
            return 0
        }

        var count = 0L
        for (trn in transactions) {
            trn.broker = toBroker
            trnRepository.save(trn)
            count++
        }
        log.info("Transferred $count transactions from broker $fromBrokerId to $toBrokerId")
        return count
    }

    private data class PortfolioTrns(
        val portfolioId: String,
        val portfolioCode: String,
        val transactions: MutableList<Trn> = mutableListOf(),
        var quantity: BigDecimal = BigDecimal.ZERO
    )

    private data class AssetAggregation(
        val assetId: String,
        val assetCode: String,
        val assetName: String?,
        val market: String,
        var quantity: BigDecimal = BigDecimal.ZERO,
        val portfolioMap: MutableMap<String, PortfolioTrns> = mutableMapOf()
    )

    /**
     * Get aggregated holdings for a specific broker for reconciliation.
     * Calculates net quantities by asset based on settled transactions.
     * Pass "NO_BROKER" to find transactions that need broker assignment.
     */
    fun getBrokerHoldings(brokerId: String): BrokerHoldingsResponse {
        val user = systemUserService.getOrThrow()

        val (brokerIdResult, brokerName, transactions) = resolveBrokerTransactions(brokerId, user)
        log.trace("Found ${transactions.size} transactions for broker: $brokerName")

        val assetMap = aggregateByAsset(transactions)
        val holdings = buildHoldingPositions(assetMap)

        log.debug("Broker holdings for $brokerName: ${holdings.size} positions")
        return BrokerHoldingsResponse(
            brokerId = brokerIdResult,
            brokerName = brokerName,
            holdings = holdings
        )
    }

    /**
     * Create weighted PROPOSED SELL transactions from the broker reconciliation
     * view — one per portfolio holding [BrokerProposalRequest.assetId] at the
     * broker, sized as `weight` * that portfolio's split-adjusted broker holding.
     * No cash auto-settle fires: proposals are PROPOSED, not SETTLED.
     */
    fun proposeWeighted(
        brokerId: String,
        request: BrokerProposalRequest
    ): TrnResponse {
        if (request.trnType != TrnType.SELL) {
            throw BusinessException("Only SELL proposals are supported")
        }
        if (request.weight <= BigDecimal.ZERO || request.weight > BigDecimal.ONE) {
            throw BusinessException("Weight must be greater than 0 and less than or equal to 1")
        }
        if (request.price <= BigDecimal.ZERO) {
            throw BusinessException("Price must be greater than 0")
        }
        if (brokerId == NO_BROKER) {
            throw BusinessException("A broker is required to propose transactions")
        }

        val holdings = getBrokerHoldings(brokerId)
        val position =
            holdings.holdings.firstOrNull { it.assetId == request.assetId }
                ?: throw NotFoundException("Asset not held at broker: ${request.assetId}")

        // Resolve trade currency up front (same fallback CSV import uses via
        // BcRowAdapter.getTradeCurrency) so FxTransactions.setRates compares
        // like-for-like currencies instead of tripping over the TrnInput
        // default empty tradeCurrency, which reads as an unknown "" currency.
        val asset = assetFinder.find(request.assetId)
        val tradeCurrencyCode = asset.accountingType?.currency?.code ?: asset.market.currency.code

        val tradeDate = request.tradeDate ?: DateUtils().date
        val created = mutableListOf<Trn>()
        for (group in position.portfolioGroups) {
            if (group.quantity.compareTo(BigDecimal.ZERO) <= 0) continue
            val quantity = group.quantity.multiply(request.weight).setScale(6, RoundingMode.HALF_UP)
            if (quantity.compareTo(BigDecimal.ZERO) == 0) continue
            val tradeAmount = quantity.multiply(request.price).setScale(2, RoundingMode.HALF_UP)
            val trnInput =
                TrnInput(
                    assetId = request.assetId,
                    trnType = TrnType.SELL,
                    quantity = quantity,
                    tradeCurrency = tradeCurrencyCode,
                    price = request.price,
                    tradeAmount = tradeAmount,
                    cashAmount = tradeAmount,
                    status = TrnStatus.PROPOSED,
                    brokerId = brokerId,
                    tradeDate = tradeDate
                )
            val portfolio = portfolioService.find(group.portfolioId)
            val result = trnService.saveWithResult(portfolio, TrnRequest(portfolio.id, listOf(trnInput)))
            created.addAll(result.trns)
        }
        return TrnResponse(created)
    }

    private fun resolveBrokerTransactions(
        brokerId: String,
        user: SystemUser
    ): Triple<String, String, Collection<Trn>> =
        if (brokerId == NO_BROKER) {
            Triple(
                NO_BROKER,
                "No Broker",
                trnRepository.findWithNoBroker(user, TrnStatus.SETTLED)
            )
        } else {
            val broker =
                brokerService.findById(brokerId, user).orElseThrow {
                    NotFoundException("Broker not found: $brokerId")
                }
            // Splits carry no broker; merge them so the quantity aggregation below is
            // split-adjusted instead of reading negative on a sold-out holding.
            val trades = trnRepository.findByBrokerIdAndOwner(brokerId, user, TrnStatus.SETTLED)
            val splits = trnRepository.findBrokerSplits(brokerId, user, DateUtils().date, TrnStatus.SETTLED)
            Triple(
                broker.id,
                broker.name,
                trades + splits
            )
        }

    // Same-day tie-breaker for split-adjusted accumulation: buys/adds, then split, then sells.
    private fun sameDayOrder(type: TrnType): Int =
        when (type) {
            TrnType.BUY, TrnType.ADD -> 0
            TrnType.SPLIT -> 1
            else -> 2
        }

    private fun aggregateByAsset(transactions: Collection<Trn>): Map<String, AssetAggregation> {
        val assetMap = mutableMapOf<String, AssetAggregation>()
        // Chronological order matters: a SPLIT multiplies whatever quantity is held at
        // that point, so earlier buys scale up and later sells draw down the scaled total.
        // Same-day events have no intra-day timestamp, so apply a deterministic order:
        // buys/adds first, then the split, then sells/reduces — matching a broker ex-date
        // where same-day trades settle against post-split share counts.
        for (trn in transactions.sortedWith(compareBy({ it.tradeDate }, { sameDayOrder(it.trnType) }))) {
            val asset = trn.asset
            val agg =
                assetMap.getOrPut(asset.id) {
                    AssetAggregation(
                        assetId = asset.id,
                        assetCode = asset.code,
                        assetName = asset.name,
                        market = asset.marketCode
                    )
                }

            val portfolioTrns =
                agg.portfolioMap.getOrPut(trn.portfolio.id) {
                    PortfolioTrns(
                        portfolioId = trn.portfolio.id,
                        portfolioCode = trn.portfolio.code
                    )
                }

            val quantityDelta =
                when (trn.trnType) {
                    TrnType.BUY, TrnType.ADD -> {
                        trn.quantity
                    }
                    TrnType.SELL, TrnType.REDUCE -> {
                        trn.quantity.negate()
                    }
                    // SPLIT quantity is the ratio (e.g. 4 = 4:1). The delta scales the
                    // running holding: newQty = qty * ratio, so delta = qty * (ratio - 1).
                    TrnType.SPLIT -> {
                        portfolioTrns.quantity
                            .multiply(trn.quantity)
                            .subtract(portfolioTrns.quantity)
                    }
                    else -> {
                        BigDecimal.ZERO
                    }
                }
            agg.quantity = agg.quantity.add(quantityDelta)
            portfolioTrns.quantity = portfolioTrns.quantity.add(quantityDelta)
            portfolioTrns.transactions.add(trn)
        }
        return assetMap
    }

    private fun buildHoldingPositions(assetMap: Map<String, AssetAggregation>): List<BrokerHoldingPosition> =
        assetMap.values
            .filter { it.quantity.compareTo(BigDecimal.ZERO) != 0 }
            .sortedBy { it.assetCode }
            .map { agg ->
                BrokerHoldingPosition(
                    assetId = agg.assetId,
                    assetCode = agg.assetCode,
                    assetName = agg.assetName,
                    market = agg.market,
                    quantity = agg.quantity,
                    portfolioGroups = buildPortfolioGroups(agg.portfolioMap.values)
                )
            }

    private fun buildPortfolioGroups(portfolios: Collection<PortfolioTrns>): List<BrokerPortfolioGroup> =
        portfolios
            .sortedBy { it.portfolioCode }
            .map { pg ->
                BrokerPortfolioGroup(
                    portfolioId = pg.portfolioId,
                    portfolioCode = pg.portfolioCode,
                    quantity = pg.quantity,
                    transactions =
                        pg.transactions
                            .sortedBy { it.tradeDate }
                            .map { trn ->
                                BrokerHoldingTransaction(
                                    id = trn.id,
                                    portfolioId = trn.portfolio.id,
                                    portfolioCode = trn.portfolio.code,
                                    tradeDate = trn.tradeDate.toString(),
                                    trnType = trn.trnType.name,
                                    quantity = trn.quantity,
                                    price = trn.price ?: BigDecimal.ZERO,
                                    tradeAmount = trn.tradeAmount
                                )
                            }
                )
            }

    /**
     * Post-process transactions with asset hydration and version migration.
     */
    private fun postProcess(trns: List<Trn>): List<Trn> {
        log.trace("PostProcess ${trns.size} transactions")
        // Asset hydration happens via AssetEntityListener @PostLoad — Trn.asset and
        // Trn.cashAsset arrive populated from JPA.
        for (trn in trns) {
            val upgraded = trnMigrator.upgrade(trn)
            if (upgraded.version != trn.version) {
                trnRepository.save(upgraded)
            }
        }
        log.trace("Completed postProcess trns: ${trns.size}")
        return trns
    }
}