package com.beancounter.common.contracts

import com.beancounter.common.model.Trn

/**
 * Response envelope for trn queries. [data] is a [TrnPayload] that
 * de-duplicates the Asset / Portfolio / Currency / Broker objects shared
 * by the contained trns.
 */
data class TrnResponse(
    override val data: TrnPayload = TrnPayload()
) : Payload<TrnPayload> {
    constructor(trns: Collection<Trn>) : this(TrnPayload.from(trns))
}