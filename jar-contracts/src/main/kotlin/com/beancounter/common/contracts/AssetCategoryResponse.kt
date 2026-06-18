package com.beancounter.common.contracts

import com.beancounter.common.model.AssetCategory

/**
 * Beancounter response to an asset category query.
 */
data class AssetCategoryResponse(
    override var data: Collection<AssetCategory>
) : Payload<Collection<AssetCategory>>