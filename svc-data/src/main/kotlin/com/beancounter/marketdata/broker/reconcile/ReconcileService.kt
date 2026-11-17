package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.exception.BusinessException
import com.beancounter.marketdata.broker.GlobalBroker
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.BrokerHoldingPosition
import com.beancounter.marketdata.trn.broker.TrnBrokerService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Compares the holdings a brokerage statement shows with what the caller holds at
 * that broker, and previews the transactions that would bring the two into line.
 * Read-only: it records nothing and creates no asset; a listing Beancounter does not have yet
 * is created when the row is recorded.
 */
@Service
class ReconcileService(
    private val portfolioService: PortfolioService,
    private val systemUserService: SystemUserService,
    private val brokerMatcher: BrokerMatcher,
    private val trnBrokerService: TrnBrokerService,
    private val shownAssetResolver: ShownAssetResolver
) {
    @Transactional(readOnly = true)
    fun preview(shown: ShownHoldings): ReconcilePreview {
        portfolioService.find(shown.portfolioId)
        if (shown.rows.size > MAX_ROWS) {
            throw BusinessException("A statement holds at most $MAX_ROWS rows, not ${shown.rows.size}")
        }
        val match = brokerMatcher.match(shown, systemUserService.getOrThrow())
        val held = match.broker?.let { trnBrokerService.getBrokerHoldings(it.id).holdings }.orEmpty()
        val rows = shown.rows.map { previewRow(it, match.firm, held) }
        return ReconcilePreview(
            globalBroker = match.firm,
            broker = match.broker?.let { MatchedBroker(it.id, it.name, it.accountNumber) },
            rows = rows,
            notShown = notShown(held, rows)
        )
    }

    private fun previewRow(
        row: ShownHolding,
        firm: GlobalBroker?,
        held: List<BrokerHoldingPosition>
    ): PreviewRow {
        val resolved = shownAssetResolver.resolve(row, firm, held)
        val assetId = resolved.asset?.id
        val quantity = assetId?.let { id -> held.firstOrNull { it.assetId == id }?.quantity }
        return RowReconciler
            .reconcile(row, resolved.market, resolved.asset, quantity ?: BigDecimal.ZERO)
            .copy(marketSource = resolved.source, listedOn = resolved.listedOn)
    }

    private fun notShown(
        held: List<BrokerHoldingPosition>,
        rows: List<PreviewRow>
    ): List<HeldNotShown> {
        val shownAssets = rows.mapNotNull { it.assetId }.toSet()
        return held
            .filter { it.quantity.signum() != 0 && it.assetId !in shownAssets }
            .map { HeldNotShown(it.assetId, it.assetCode, it.market, it.quantity) }
    }

    companion object {
        /** More rows than any statement screenshot shows; bounds the asset lookups of one call. */
        const val MAX_ROWS = 500
    }
}