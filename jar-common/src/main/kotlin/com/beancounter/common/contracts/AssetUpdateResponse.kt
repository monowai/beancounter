package com.beancounter.common.contracts

import com.beancounter.common.model.Asset

/**
 * In response to upsert request for a collection of assets.
 * The supplied identifier can be used by the caller find the asset result in the generated response.
 */
data class AssetUpdateResponse constructor(override var data: Map<String, Asset>) :
    Payload<Map<String, Asset>>
