package com.beancounter.common.contracts

/**
 * TrnId's that were deleted
 */
data class TrnDeleteResponse(
    override val data: Collection<String> = arrayListOf()
) : Payload<Collection<String>>