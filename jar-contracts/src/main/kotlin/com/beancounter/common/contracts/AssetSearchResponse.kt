package com.beancounter.common.contracts

/**
 * Results of a Search Request.
 */
data class AssetSearchResponse(
    override var data: Collection<AssetSearchResult>
) : Payload<Collection<AssetSearchResult>>