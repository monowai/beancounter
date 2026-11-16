package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.contracts.Payload
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.broker.GlobalBroker
import java.math.BigDecimal

/**
 * What a brokerage statement shows, as read from a screenshot.
 *
 * @property portfolioId the portfolio the holdings would be recorded in
 * @property brokerId the caller's broker, when the user has already chosen it
 * @property brokerName the firm's name as printed on the statement
 * @property accountNumber the visible digits of the account number, e.g. "482"
 */
data class ShownHoldings(
    val portfolioId: String,
    val brokerId: String? = null,
    val brokerName: String? = null,
    val accountNumber: String? = null,
    val rows: List<ShownHolding> = emptyList()
)

/**
 * One holding on the statement. Only [code] is certain to be legible.
 *
 * @property costBasis the total cost of the holding
 */
data class ShownHolding(
    val code: String,
    val market: String? = null,
    val quantity: BigDecimal? = null,
    val costBasis: BigDecimal? = null,
    val costPerShare: BigDecimal? = null,
    val price: BigDecimal? = null,
    val marketValue: BigDecimal? = null,
    val gainLoss: BigDecimal? = null
)

/**
 * What recording a statement would do. Nothing has been recorded.
 *
 * @property globalBroker the firm the statement names, if known
 * @property broker the caller's own broker, if exactly one fits
 * @property notShown held at [broker] but absent from the statement; a statement can be
 * partial, so no transaction is proposed for these
 */
data class ReconcilePreview(
    val globalBroker: GlobalBroker?,
    val broker: MatchedBroker?,
    val rows: List<PreviewRow>,
    val notShown: List<HeldNotShown>
)

data class MatchedBroker(
    val id: String,
    val name: String,
    val accountNumber: String?
)

/**
 * A shown holding beside what is held, and the transaction that would close the gap.
 *
 * @property trnType ADD or REDUCE; null unless [status] is READY or NEEDS_COST
 * @property quantity the absolute difference between [shown] and [held]
 * @property cost the cost of [quantity], for an ADD
 */
data class PreviewRow(
    val code: String,
    val market: String?,
    val assetId: String?,
    val assetName: String?,
    val shown: BigDecimal?,
    val held: BigDecimal,
    val trnType: TrnType?,
    val quantity: BigDecimal?,
    val cost: BigDecimal?,
    val status: RowStatus,
    val warnings: List<RowWarning>
)

enum class RowStatus {
    READY,
    UNCHANGED,
    NEEDS_QUANTITY,
    NEEDS_MARKET,
    UNRESOLVED,
    NEEDS_COST
}

enum class RowWarning {
    MARKET_VALUE_MISMATCH,
    GAIN_LOSS_MISMATCH
}

data class HeldNotShown(
    val assetId: String,
    val code: String,
    val market: String,
    val quantity: BigDecimal
)

data class ReconcilePreviewResponse(
    override val data: ReconcilePreview
) : Payload<ReconcilePreview>