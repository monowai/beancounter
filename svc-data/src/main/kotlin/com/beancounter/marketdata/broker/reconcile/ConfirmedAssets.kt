package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.trn.CashTrnServices
import org.springframework.stereotype.Component

/** The assets a confirmed submission names: found, or created for a listing new to Beancounter. */
@Component
class ConfirmedAssets(
    private val assetFinder: AssetFinder,
    private val assetService: AssetService,
    private val cashTrnServices: CashTrnServices
) {
    /**
     * [row] named by id: as it is, or with the id of the asset its market and code now create.
     * The code is read as the preview read it, so a shown BRK/B records BRK.B.
     */
    fun withAssetId(row: ConfirmedRow): ConfirmedRow {
        if (!row.assetId.isNullOrBlank()) return row
        val code = ShownAssetResolver.beancounterCode(checkNotNull(row.code))
        val created = assetService.handle(AssetRequest(AssetInput(checkNotNull(row.market), code), code))
        return row.copy(assetId = created.data.getValue(code).id)
    }

    /**
     * The currency the transaction mapper falls back to when a request names none. An asset
     * that does not exist fails here, before any write.
     */
    fun tradeCurrency(assetId: String): String {
        val asset = assetFinder.find(assetId)
        return (asset.accountingType?.currency ?: asset.market.currency).code
    }

    /**
     * The id of the cash asset a deposit in [currency] at [brokerId] lands in: the broker's
     * settlement account for the currency, else the generic cash balance for it.
     */
    fun cashAssetId(
        currency: String,
        ownerId: String,
        brokerId: String
    ): String =
        checkNotNull(cashTrnServices.getCashAsset(TrnType.DEPOSIT, null, currency, ownerId, brokerId)) {
            "No cash asset for $currency"
        }.id
}