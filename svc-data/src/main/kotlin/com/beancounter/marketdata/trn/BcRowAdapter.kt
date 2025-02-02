package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.ingest.RowAdapter
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils
import com.beancounter.marketdata.trn.TrnIoDefinition.Columns
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Maps BC compatible delimited data to the domain model
 */
@Service
class BcRowAdapter(
    val assetIngestService: AssetIngestService,
    val cashTrnServices: CashTrnServices,
    val dateUtils: DateUtils = DateUtils()
) : RowAdapter {
    override fun transform(trustedTrnImportRequest: TrustedTrnImportRequest): TrnInput {
        val tradeDate = dateUtils.getDate(trustedTrnImportRequest.row[Columns.Date.ordinal].trim())
        if (tradeDate.isAfter(LocalDate.now())) {
            throw BusinessException("Rejecting the forward dated trade date of $tradeDate")
        }

        val trnType = TrnType.valueOf(trustedTrnImportRequest.row[Columns.Type.ordinal].trim())
        val marketCode = trustedTrnImportRequest.row[Columns.Market.ordinal].trim()
        val assetCode = trustedTrnImportRequest.row[Columns.Code.ordinal].trim()

        val asset =
            assetIngestService.resolveAsset(
                AssetInput(
                    market = marketCode,
                    code = assetCode,
                    name = trustedTrnImportRequest.row[Columns.Name.ordinal].trim(),
                    owner = trustedTrnImportRequest.portfolio.owner.id
                )
            )
        val cashCurrency = trustedTrnImportRequest.row[Columns.CashCurrency.ordinal].trim()
        val cashAccount = trustedTrnImportRequest.row[Columns.CashAccount.ordinal].trim()
        val cashAssetId =
            getCashAssetId(
                trnType,
                asset,
                cashAccount,
                cashCurrency
            )
        val quantity =
            MathUtils.nullSafe(
                MathUtils.parse(trustedTrnImportRequest.row[Columns.Quantity.ordinal])
            )
        val price =
            MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[Columns.Price.ordinal]))
        val fees =
            MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[Columns.Fees.ordinal]))
        val tradeBaseRate = MathUtils.parse(trustedTrnImportRequest.row[Columns.BaseRate.ordinal])
        val tradeAmount =
            MathUtils.nullSafe(
                MathUtils.parse(trustedTrnImportRequest.row[Columns.TradeAmount.ordinal])
            )

        val tradeCurrency =
            if (trustedTrnImportRequest.row[Columns.TradeCurrency.ordinal].isEmpty()) {
                asset.market.currency.code
            } else {
                trustedTrnImportRequest.row[Columns.TradeCurrency.ordinal].trim()
            }

        return TrnInput(
            callerRef =
                CallerRef(
                    // SystemUserId
                    trustedTrnImportRequest.portfolio.owner.id,
                    trustedTrnImportRequest.row[Columns.Batch.ordinal].trim(),
                    trustedTrnImportRequest.row[Columns.CallerId.ordinal].trim()
                ),
            assetId = asset.id,
            trnType = trnType,
            quantity = quantity,
            tradeCurrency = tradeCurrency,
            tradeBaseRate = tradeBaseRate,
            tradeDate = tradeDate,
            tradeAmount = tradeAmount,
            cashAmount = MathUtils.parse(trustedTrnImportRequest.row[Columns.CashAmount.ordinal]),
            cashCurrency = cashCurrency,
            cashAssetId = cashAssetId,
            fees = fees,
            price = price,
            comments = trustedTrnImportRequest.row[Columns.Comments.ordinal]
        )
    }

    private fun getCashAssetId(
        trnType: TrnType,
        asset: Asset,
        cashAccount: String,
        cashCurrency: String
    ): String? {
        val cashAsset: Asset? =
            if (TrnType.isCash(trnType)) {
                asset
            } else {
                cashTrnServices.getCashAsset(
                    trnType,
                    cashAccount,
                    cashCurrency
                )
            }
        return cashAsset?.id
    }
}