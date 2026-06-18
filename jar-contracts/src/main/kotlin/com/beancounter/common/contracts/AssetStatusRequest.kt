package com.beancounter.common.contracts

import com.beancounter.common.model.Status

/**
 * Request to update asset status.
 */
data class AssetStatusRequest(
    val status: Status
)