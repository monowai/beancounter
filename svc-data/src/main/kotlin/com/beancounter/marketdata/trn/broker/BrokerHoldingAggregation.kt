package com.beancounter.marketdata.trn.broker

import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.trn.BrokerHoldingPosition
import com.beancounter.marketdata.trn.BrokerHoldingTransaction
import com.beancounter.marketdata.trn.BrokerPortfolioGroup
import java.math.BigDecimal

/**
 * Nets settled trns into broker holding positions: per asset, then per portfolio within the
 * asset, applying splits to the quantity held at the time they occur.
 */
internal object BrokerHoldingAggregation {
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
        val boardLot: Int,
        var quantity: BigDecimal = BigDecimal.ZERO,
        val portfolioMap: MutableMap<String, PortfolioTrns> = mutableMapOf()
    )

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
                        market = asset.marketCode,
                        boardLot = asset.accountingType?.boardLot ?: 1
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

    /**
     * Holdings for [transactions] by asset, sorted by asset code. Assets that net to zero are
     * dropped; sold-out portfolio groups are kept so reconciliation shows their history.
     */
    fun holdings(transactions: Collection<Trn>): List<BrokerHoldingPosition> =
        aggregateByAsset(transactions)
            .values
            .filter { it.quantity.compareTo(BigDecimal.ZERO) != 0 }
            .sortedBy { it.assetCode }
            .map { agg ->
                BrokerHoldingPosition(
                    assetId = agg.assetId,
                    assetCode = agg.assetCode,
                    assetName = agg.assetName,
                    market = agg.market,
                    quantity = agg.quantity,
                    boardLot = agg.boardLot,
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
     * Aggregate [transactions] (all for the same [assetId]) into a single
     * [BrokerHoldingPosition], excluding zero-quantity portfolio groups. Returns
     * null when the asset's net quantity across all groups is zero, or every
     * portfolio group nets to zero.
     */
    fun assetHolding(
        assetId: String,
        transactions: Collection<Trn>
    ): BrokerHoldingPosition? {
        val agg = aggregateByAsset(transactions)[assetId] ?: return null
        if (agg.quantity.compareTo(BigDecimal.ZERO) == 0) return null
        val portfolioGroups =
            buildPortfolioGroups(agg.portfolioMap.values)
                .filter { it.quantity.compareTo(BigDecimal.ZERO) != 0 }
        if (portfolioGroups.isEmpty()) return null
        return BrokerHoldingPosition(
            assetId = agg.assetId,
            assetCode = agg.assetCode,
            assetName = agg.assetName,
            market = agg.market,
            quantity = agg.quantity,
            boardLot = agg.boardLot,
            portfolioGroups = portfolioGroups
        )
    }
}