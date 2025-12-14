package com.beancounter.common.utils

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import org.springframework.stereotype.Service

/**
 * Cash and cash-like asset utilities.
 * ACCOUNT assets (bank accounts) are treated like CASH for pricing purposes.
 */
@Service
class CashUtils {
    fun isCash(asset: Asset): Boolean =
        asset.assetCategory.id == AssetCategory.CASH ||
            asset.assetCategory.id == AssetCategory.ACCOUNT
}