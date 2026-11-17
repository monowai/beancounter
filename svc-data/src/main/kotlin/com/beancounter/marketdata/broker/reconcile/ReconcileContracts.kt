package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.contracts.Payload
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.broker.GlobalBroker
import java.math.BigDecimal
import java.time.LocalDate

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
 * @property assetId Beancounter's asset; null for a listing it does not have yet, which
 * recording creates under [assetCode]
 * @property assetCode the code Beancounter knows the asset by, e.g. BRK.B for a shown BRK/B
 * @property marketSource where [market] came from; null when there is none
 * @property listedOn the markets that list [code], when they were looked up
 */
data class PreviewRow(
    val code: String,
    val market: String?,
    val assetId: String?,
    val assetCode: String? = null,
    val assetName: String?,
    val shown: BigDecimal?,
    val held: BigDecimal,
    val trnType: TrnType?,
    val quantity: BigDecimal?,
    val cost: BigDecimal?,
    val status: RowStatus,
    val warnings: List<RowWarning>,
    val marketSource: MarketSource? = null,
    val listedOn: List<String> = emptyList()
)

/** Where a preview row's market came from, strongest first. */
enum class MarketSource {
    /** The row named it: printed on the statement or chosen by the user. */
    SHOWN,

    /** The broker already holds an asset with this code there. */
    HELD,

    /** The firm's market, which lists the code or is the best guess. */
    BROKERAGE,

    /** The only market that lists the code. */
    LISTED
}

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

/**
 * The rows of a previewed statement as the user confirmed them, to be recorded.
 *
 * @property uploadId identifies the uploaded statement; one upload is recorded once
 * @property tradeDate the date the transactions carry; today when absent
 */
data class ConfirmedHoldings(
    val portfolioId: String,
    val brokerId: String,
    val uploadId: String,
    val tradeDate: LocalDate? = null,
    val rows: List<ConfirmedRow>
)

/**
 * One transaction to record. The asset is named by [assetId], or by [market] and [code]
 * for a listing Beancounter does not have yet, which recording creates.
 *
 * @property trnType ADD or REDUCE
 * @property cost the total cost of [quantity] in the asset's trade currency; an ADD needs one
 */
data class ConfirmedRow(
    val assetId: String?,
    val trnType: TrnType,
    val quantity: BigDecimal,
    val cost: BigDecimal? = null,
    val market: String? = null,
    val code: String? = null
) {
    /** How the row's asset is named in a message. */
    val label: String
        get() = assetId ?: "$market:$code"

    /** Named by id, or by market and code. */
    val named: Boolean
        get() = !assetId.isNullOrBlank() || (!market.isNullOrBlank() && !code.isNullOrBlank())
}