package com.beancounter.common.contracts

import com.beancounter.common.model.Asset

/**
 * Response to an asset request.
 */
data class AssetsResponse(
    override val data: List<Asset> = emptyList()
) : Payload<List<Asset>>