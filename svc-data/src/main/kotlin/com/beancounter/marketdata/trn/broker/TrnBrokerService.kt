package com.beancounter.marketdata.trn.broker

import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.broker.BrokerService
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.AssetBrokerHolding
import com.beancounter.marketdata.trn.AssetBrokerHoldingsResponse
import com.beancounter.marketdata.trn.BrokerHoldingsResponse
import com.beancounter.marketdata.trn.TrnMigrator
import com.beancounter.marketdata.trn.TrnRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for broker-related transaction operations including holdings aggregation and transfers.
 */
@Service
@Transactional
class TrnBrokerService(
    private val trnRepository: TrnRepository,
    private val brokerService: BrokerService,
    private val systemUserService: SystemUserService,
    private val trnMigrator: TrnMigrator
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

    /**
     * Get aggregated holdings for a specific broker for reconciliation.
     * Calculates net quantities by asset based on settled transactions.
     * Pass "NO_BROKER" to find transactions that need broker assignment.
     */
    fun getBrokerHoldings(brokerId: String): BrokerHoldingsResponse {
        val user = systemUserService.getOrThrow()

        val (brokerIdResult, brokerName, transactions) = resolveBrokerTransactions(brokerId, user)
        log.trace("Found ${transactions.size} transactions for broker: $brokerName")

        val holdings = BrokerHoldingAggregation.holdings(transactions)

        log.debug("Broker holdings for $brokerName: ${holdings.size} positions")
        return BrokerHoldingsResponse(
            brokerId = brokerIdResult,
            brokerName = brokerName,
            holdings = holdings
        )
    }

    /**
     * Reverse lookup: which broker(s) hold [assetId] for the calling user, shaped
     * for the frontend's WeightedSellDialog. Mirrors [getBrokerHoldings]'s owner
     * scoping, SETTLED-only status, and split-adjustment handling, but groups by
     * broker for a single asset instead of by asset for a single broker.
     *
     * Unlike [getBrokerHoldings] (the broker reconciliation view, which
     * intentionally keeps sold-out portfolio groups for context), zero-quantity
     * portfolio groups are excluded here — this feeds a sell picker, so only
     * actionable holdings are relevant.
     */
    fun getAssetBrokerHoldings(assetId: String): AssetBrokerHoldingsResponse {
        val user = systemUserService.getOrThrow()

        val brokerTrades = trnRepository.findByAssetAndOwner(assetId, user, TrnStatus.SETTLED)
        val noBrokerTrades = trnRepository.findWithNoBrokerByAssetAndOwner(assetId, user, TrnStatus.SETTLED)
        if (brokerTrades.isEmpty() && noBrokerTrades.isEmpty()) {
            return AssetBrokerHoldingsResponse()
        }
        val splits = trnRepository.findSplitsByAssetAndOwner(assetId, user, DateUtils().date, TrnStatus.SETTLED)

        val groups = linkedMapOf<String, MutableList<Trn>>()
        val brokerNames = mutableMapOf<String, String>()
        for (trn in brokerTrades) {
            val broker = trn.broker ?: continue
            groups.getOrPut(broker.id) { mutableListOf() }.add(trn)
            brokerNames[broker.id] = broker.name
        }
        if (noBrokerTrades.isNotEmpty()) {
            groups[NO_BROKER] = noBrokerTrades.toMutableList()
            brokerNames[NO_BROKER] = "No Broker"
        }

        val results = mutableListOf<AssetBrokerHolding>()
        for ((brokerId, trns) in groups) {
            val portfolioIds = trns.map { it.portfolio.id }.toSet()
            val relevantSplits = splits.filter { it.portfolio.id in portfolioIds }
            val holding = BrokerHoldingAggregation.assetHolding(assetId, trns + relevantSplits) ?: continue
            results.add(
                AssetBrokerHolding(
                    brokerId = brokerId,
                    brokerName = brokerNames.getValue(brokerId),
                    holding = holding
                )
            )
        }

        return AssetBrokerHoldingsResponse(data = results.sortedBy { it.brokerName })
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
            // Splits carry no broker; merge them so BrokerHoldingAggregation is
            // split-adjusted instead of reading negative on a sold-out holding.
            val trades = trnRepository.findByBrokerIdAndOwner(brokerId, user, TrnStatus.SETTLED)
            val splits = trnRepository.findBrokerSplits(brokerId, user, DateUtils().date, TrnStatus.SETTLED)
            Triple(
                broker.id,
                broker.name,
                trades + splits
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