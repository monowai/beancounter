package com.beancounter.client

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.model.Asset

interface AssetService {
    fun process(assetRequest: AssetRequest): AssetUpdateResponse?
    fun backFillEvents(assetId: String)
    fun find(assetId: String): Asset
}