package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.trn.CashTrnServices
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** The assets a confirmed submission names: found, or created for a listing new to Beancounter. */
@Component
class ConfirmedAssets(
    private val assetFinder: AssetFinder,
    private val assetService: AssetService,
    private val cashTrnServices: CashTrnServices,
    private val importSplits: ImportSplits
) {
    /**
     * [row] in the share basis of its own date, with a note saying so when that differs from
     * the record date's. A statement shows the quantity held on [recordDate]; a row dated
     * earlier is divided by the splits in between, so the split transactions bring it back to
     * the quantity shown. The cost is unchanged, so the price per share rises to match. Where
     * the splits in between cannot be known, the row is kept as shown and the note says so.
     */
    fun onItsDate(
        row: ConfirmedRow,
        recordDate: LocalDate
    ): Pair<ConfirmedRow, String?> {
        val date = row.tradeDate?.takeIf { it.isBefore(recordDate) } ?: return row to null
        val factor =
            importSplits.factor(row.label, date, recordDate)
                ?: return row to
                    "Imported as ${row.quantity.toPlainString()} held on $recordDate: split history " +
                    "before $recordDate was unavailable, so any splits since $date are not applied"
        if (factor.compareTo(BigDecimal.ONE) == 0) return row to null
        val quantity = row.quantity.divide(factor, QUANTITY_SCALE, RoundingMode.HALF_UP).stripTrailingZeros()
        if (quantity.signum() <= 0) {
            throw BusinessException(
                "Asset ${row.label}: ${row.quantity.toPlainString()} held on $recordDate is less than the " +
                    "smallest quantity recordable on $date, before splits of ${factor.toPlainString()} since"
            )
        }
        val note =
            "Imported as ${row.quantity.toPlainString()} held on $recordDate: " +
                "${quantity.toPlainString()} on $date, before splits of ${factor.toPlainString()} since"
        return row.copy(quantity = quantity) to note
    }

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

    private companion object {
        /** The scale of the stored quantity column. */
        const val QUANTITY_SCALE = 6
    }
}