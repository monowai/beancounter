package com.beancounter.common.contracts

import com.beancounter.common.utils.DateUtils
import java.math.BigDecimal

/**
 * Uses defined pricing for an asset, e.g. House, Art etc.
 */
data class OffMarketPriceRequest(
    val assetId: String,
    val date: String = DateUtils().today(),
    val closePrice: BigDecimal,
)
