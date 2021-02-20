package com.beancounter.common.contracts

data class AssetSearchResponse(override var data: Collection<AssetSearchResult>) : Payload<Collection<AssetSearchResult>>
