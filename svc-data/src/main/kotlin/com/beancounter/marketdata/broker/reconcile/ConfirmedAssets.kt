package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import org.springframework.stereotype.Component

/** The assets a confirmed submission names: found, or created for a listing new to Beancounter. */
@Component
class ConfirmedAssets(
    private val assetFinder: AssetFinder,
    private val assetService: AssetService
) {
    /**
     * [row] named by id: as it is, or with the id of the asset its market and code now create.
     * The code is read as the preview read it, so a shown BRK/B records BRK.B.
     */
    fun withAssetId(row: ConfirmedRow): ConfirmedRow {
        if (!row.assetId.isNullOrBlank()) return row
        return row.copy(assetId = idFor(checkNotNull(row.market), checkNotNull(row.code)))
    }

    /** The id of the asset [market] and [code] name, created when Beancounter does not have it yet. */
    fun idFor(
        market: String,
        code: String
    ): String {
        val beancounterCode = ShownAssetResolver.beancounterCode(code)
        val created = assetService.handle(AssetRequest(AssetInput(market, beancounterCode), beancounterCode))
        return created.data.getValue(beancounterCode).id
    }

    /**
     * The currency the transaction mapper falls back to when a request names none. An asset
     * that does not exist fails here, before any write.
     */
    fun tradeCurrency(assetId: String): String {
        val asset = assetFinder.find(assetId)
        return (asset.accountingType?.currency ?: asset.market.currency).code
    }
}