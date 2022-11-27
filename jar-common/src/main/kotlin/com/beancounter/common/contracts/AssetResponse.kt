package com.beancounter.common.contracts

import com.beancounter.common.model.Asset

/**
 * Response to an asset request.
 */
data class AssetResponse constructor(override val data: Asset) : Payload<Asset?>
