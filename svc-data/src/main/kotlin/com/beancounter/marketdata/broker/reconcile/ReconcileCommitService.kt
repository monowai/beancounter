package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnSaveResult
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
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
    private val confirmedAssets: ConfirmedAssets,
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
        val confirmed = submitted.copy(rows = submitted.rows.map { confirmedAssets.withAssetId(it) })
        ConfirmedRowRules.checkResolved(confirmed)
        // An asset that does not exist fails here, before any write. The currency is the one
        // the transaction mapper falls back to when a request names none.
        val tradeCurrencies =
            confirmed.rows.associate { row -> row.label to confirmedAssets.tradeCurrency(row.label) }
        // Before the holdings check: a repeated upload's reductions no longer fit what is
        // held, and the user should be told it is recorded, not that it reduces too much.
        commitGuard.refuseRecorded(confirmed.uploadId, owner)
        commitGuard.refuseOverReduction(confirmed)
        val tradeDate = confirmed.tradeDate ?: DateUtils().date
        val recordedBy = ReconcileCommitGuard.recordedBy(owner)
        val inputs =
            confirmed.rows.map { row ->
                trnInput(row, row.tradeDate ?: tradeDate).copy(
                    brokerId = broker.id,
                    tradeCurrency = tradeCurrencies.getValue(row.label),
                    // A caller reference is unique across all users: without the owner, two users
                    // who send the same upload id for the same asset would collide in the database.
                    callerRef = CallerRef(ReconcileCommitGuard.PROVIDER, confirmed.uploadId, recordedBy + row.label)
                )
            }
        val deposit =
            confirmed.cash?.let { cash ->
                depositInput(cash, confirmedAssets.cashAssetId(cash.currency, owner.id, broker.id), tradeDate).copy(
                    brokerId = broker.id,
                    callerRef = CallerRef(ReconcileCommitGuard.PROVIDER, confirmed.uploadId, recordedBy + CASH_REF)
                )
            }
        // One save: a refusal of any holding records no cash, and the reverse.
        return trnService.saveWithResult(portfolio, TrnRequest(portfolio.id, inputs + listOfNotNull(deposit)))
    }

    /**
     * The account's opening cash as one settled deposit into [cashAssetId], shaped as the
     * Set Balance dialog sends one: the asset is its own settlement account.
     */
    private fun depositInput(
        cash: ConfirmedCash,
        cashAssetId: String,
        tradeDate: LocalDate
    ) = TrnInput(
        assetId = cashAssetId,
        cashAssetId = cashAssetId,
        trnType = TrnType.DEPOSIT,
        quantity = cash.amount,
        cashAmount = cash.amount,
        tradeCurrency = cash.currency,
        cashCurrency = cash.currency,
        tradeDate = tradeDate,
        status = TrnStatus.SETTLED,
        comments = OPENING_CASH
    )

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

        /** The caller id suffix of the opening cash deposit; no row label, an id or market:code, is "cash". */
        private const val CASH_REF = "cash"

        private const val OPENING_CASH = "Opening cash balance"
    }
}