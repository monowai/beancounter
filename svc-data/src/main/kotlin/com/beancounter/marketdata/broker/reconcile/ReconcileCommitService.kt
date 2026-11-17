package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnSaveResult
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.broker.BrokerService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TrnService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Records the rows a user confirmed from a previewed brokerage statement, one transaction
 * per row. Every rule is checked before the first write, and the write is all or nothing.
 */
@Service
class ReconcileCommitService(
    private val portfolioService: PortfolioService,
    private val systemUserService: SystemUserService,
    private val brokerService: BrokerService,
    private val assetFinder: AssetFinder,
    private val assetService: AssetService,
    private val commitGuard: ReconcileCommitGuard,
    private val trnService: TrnService
) {
    @Transactional
    fun commit(submitted: ConfirmedHoldings): TrnSaveResult {
        val portfolio = portfolioService.find(submitted.portfolioId)
        val owner = systemUserService.getOrThrow()
        // The transaction mapper resolves a broker id without asking whose it is (#1330).
        val broker =
            brokerService
                .findById(submitted.brokerId, owner)
                .orElseThrow { NotFoundException("Broker not found: ${submitted.brokerId}") }
        ConfirmedRowRules.check(submitted)
        // A listing Beancounter does not have yet is created here; a later refusal rolls it back.
        val confirmed = submitted.copy(rows = submitted.rows.map { withAssetId(it) })
        // An asset that does not exist fails here, before any write. The currency is the one
        // the transaction mapper falls back to when a request names none.
        val tradeCurrencies =
            confirmed.rows.associate { row ->
                val asset = assetFinder.find(row.label)
                row.label to (asset.accountingType?.currency ?: asset.market.currency).code
            }
        // Before the holdings check: a repeated upload's reductions no longer fit what is
        // held, and the user should be told it is recorded, not that it reduces too much.
        commitGuard.refuseRecorded(confirmed.uploadId, owner)
        commitGuard.refuseOverReduction(confirmed)
        val tradeDate = confirmed.tradeDate ?: DateUtils().date
        val inputs =
            confirmed.rows.map { row ->
                trnInput(row, tradeDate).copy(
                    brokerId = broker.id,
                    tradeCurrency = tradeCurrencies.getValue(row.label),
                    // A caller reference is unique across all users: without the owner, two users
                    // who send the same upload id for the same asset would collide in the database.
                    callerRef =
                        CallerRef(
                            ReconcileCommitGuard.PROVIDER,
                            confirmed.uploadId,
                            ReconcileCommitGuard.recordedBy(owner) + row.label
                        )
                )
            }
        return trnService.saveWithResult(portfolio, TrnRequest(portfolio.id, inputs))
    }

    /** [row] named by id: as it is, or with the id of the asset its market and code now create. */
    private fun withAssetId(row: ConfirmedRow): ConfirmedRow {
        if (!row.assetId.isNullOrBlank()) return row
        val code = checkNotNull(row.code)
        val created = assetService.handle(AssetRequest(AssetInput(checkNotNull(row.market), code), code))
        return row.copy(assetId = created.data.getValue(code).id)
    }

    /**
     * An ADD carries its cost. A REDUCE carries no amount: the position service then takes the
     * quantity out at average cost and realises no gain, where an invented amount would.
     */
    private fun trnInput(
        row: ConfirmedRow,
        tradeDate: LocalDate
    ): TrnInput {
        val cost = row.cost?.takeIf { row.trnType == TrnType.ADD } ?: BigDecimal.ZERO
        return TrnInput(
            assetId = row.label,
            trnType = row.trnType,
            quantity = row.quantity,
            tradeDate = tradeDate,
            tradeAmount = cost,
            price = cost.divide(row.quantity, PRICE_SCALE, RoundingMode.HALF_UP),
            status = TrnStatus.SETTLED
        )
    }

    companion object {
        /** The scale of the stored price column. */
        private const val PRICE_SCALE = 6
    }
}