package com.beancounter.common.contracts

import com.beancounter.common.model.Trn

/**
 * Response envelope for trn queries. [data] is a [TrnPayload] that
 * de-duplicates the Asset / Portfolio / Currency / Broker objects shared
 * by the contained trns.
 */
data class TrnResponse(
    override val data: TrnPayload = TrnPayload(),
    // Non-fatal hints raised while persisting / settling the transactions
    // (e.g. auto-settle skipped because the funding portfolio has no balance
    // in the trade's settlement currency). UI surfaces these to the user.
    val warnings: List<String> = emptyList(),
    // Split-adjusted quantity per group for trade drill-downs. Null on
    // responses that are not an asset trade list.
    val summary: TrnTradeSummary? = null
) : Payload<TrnPayload> {
    constructor(trns: Collection<Trn>) : this(TrnPayload.from(trns))

    constructor(
        trns: Collection<Trn>,
        warnings: List<String>
    ) : this(TrnPayload.from(trns), warnings)

    constructor(
        trns: Collection<Trn>,
        summary: TrnTradeSummary?
    ) : this(TrnPayload.from(trns), emptyList(), summary)
}