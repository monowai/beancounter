package com.beancounter.common.contracts

import com.beancounter.common.model.Trn

/**
 * Responses to a TrnRequest.
 */
data class TrnResponse(
    override val data: Collection<Trn> = arrayListOf(),
) : Payload<Collection<Trn>>
