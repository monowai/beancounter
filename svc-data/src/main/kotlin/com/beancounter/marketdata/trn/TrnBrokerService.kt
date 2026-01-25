package com.beancounter.marketdata.trn

import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.broker.BrokerService
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

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
                trnRepository.findAllByBrokerIdForPositions(
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
        val isNoBroker = brokerId == NO_BROKER

        val (brokerIdResult, brokerName, transactions) =
            if (isNoBroker) {
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
                Triple(
                    broker.id,
                    broker.name,
                    trnRepository.findByBrokerIdAndOwner(brokerId, user, TrnStatus.SETTLED)
                )
            }

        log.trace("Found ${transactions.size} transactions for broker: $brokerName")

        // Group transactions by asset, then by portfolio
        data class PortfolioTrns(
            val portfolioId: String,
            val portfolioCode: String,
            val transactions: MutableList<Trn> = mutableListOf(),
            var quantity: BigDecimal = BigDecimal.ZERO
        )

        data class AssetAggregation(
            val assetId: String,
            val assetCode: String,
            val assetName: String?,
            val market: String,
            var quantity: BigDecimal = BigDecimal.ZERO,
            val portfolioMap: MutableMap<String, PortfolioTrns> = mutableMapOf()
        )

        val assetMap = mutableMapOf<String, AssetAggregation>()

        for (trn in transactions) {
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

            // Accumulate quantity based on transaction type
            val quantityDelta =
                when (trn.trnType) {
                    TrnType.BUY, TrnType.ADD -> trn.quantity
                    TrnType.SELL, TrnType.REDUCE -> trn.quantity.negate()
                    else -> BigDecimal.ZERO
                }
            agg.quantity = agg.quantity.add(quantityDelta)
            portfolioTrns.quantity = portfolioTrns.quantity.add(quantityDelta)
            portfolioTrns.transactions.add(trn)
        }

        // Filter out positions with zero quantity and convert to response DTOs
        val holdings =
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
                        portfolioGroups =
                            agg.portfolioMap.values
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
                    )
                }

        log.debug("Broker holdings for $brokerName: ${holdings.size} positions")
        return BrokerHoldingsResponse(
            brokerId = brokerIdResult,
            brokerName = brokerName,
            holdings = holdings
        )
    }

    /**
     * Post-process transactions with asset hydration and version migration.
     */
    private fun postProcess(trns: List<Trn>): List<Trn> {
        log.trace("PostProcess ${trns.size} transactions")
        val assets =
            trns
                .flatMap {
                    listOfNotNull(
                        assetFinder.hydrateAsset(it.asset),
                        it.cashAsset?.let { cashAsset -> assetFinder.hydrateAsset(cashAsset) }
                    )
                }.associateBy { it.id }
        log.trace("PostProcess ${assets.size} assets")
        for (trn in trns) {
            trn.asset = assets[trn.asset.id]!!
            trn.cashAsset = trn.cashAsset?.let { assets[it.id] }
            val upgraded = trnMigrator.upgrade(trn)
            if (upgraded.version != trn.version) {
                trnRepository.save(upgraded)
            }
        }
        log.trace("Completed postProcess trns: ${trns.size}")
        return trns
    }
}