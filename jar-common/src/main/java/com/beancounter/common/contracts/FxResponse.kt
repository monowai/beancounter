package com.beancounter.common.contracts

data class FxResponse(override val data: FxPairResults = FxPairResults()) : Payload<FxPairResults>
