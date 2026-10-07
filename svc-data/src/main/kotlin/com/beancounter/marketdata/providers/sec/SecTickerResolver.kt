package com.beancounter.marketdata.providers.sec

import com.beancounter.common.model.Asset
import org.springframework.stereotype.Component

/**
 * Resolves a Beancounter asset code to the ten-digit CIK that `data.sec.gov` endpoints key on.
 *
 * SEC spells share classes with a hyphen (`BRK-B`) where Beancounter uses a dot (`BRK.B`), so
 * the code is upper-cased and dot-to-hyphen translated before the lookup.
 */
@Component
class SecTickerResolver(
    private val directory: SecTickerDirectory
) {
    fun resolve(asset: Asset): String? = directory.tickerToCik()[normalise(asset.code)]

    private fun normalise(code: String): String = code.trim().uppercase().replace('.', '-')
}