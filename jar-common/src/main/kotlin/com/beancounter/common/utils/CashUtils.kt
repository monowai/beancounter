package com.beancounter.common.utils

import com.beancounter.common.model.Asset
import org.springframework.stereotype.Service

/**
 * Percentage related utils.
 */
@Service
class CashUtils {
    fun isCash(asset: Asset): Boolean = asset.assetCategory.id == "CASH"
}