package com.beancounter.common.contracts

/**
 * Response to a FxRequest.
 */
data class FxResponse(
    override val data: FxPairResults = FxPairResults()
) : Payload<FxPairResults>