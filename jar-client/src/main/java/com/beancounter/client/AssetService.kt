package com.beancounter.client

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.model.Asset

/**
 * Minimal asset related services needed by a client that has to be implemented by the server.
 */
interface AssetService {
    /**
     * Create/Locate the requested assets and return a response.
     */
    fun process(assetRequest: AssetRequest): AssetUpdateResponse?
    /**
     * Locate missing corporate actions for the supplied assetId.
     */
    fun backFillEvents(assetId: String)
    /**
     * Find a single Asset
     */
    fun find(assetId: String): Asset
}
