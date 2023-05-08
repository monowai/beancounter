package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.ingest.RowAdapter
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils
import com.beancounter.marketdata.trn.TrnIoDefinition.Columns
import com.beancounter.marketdata.trn.TrnIoDefinition.Companion.colDef
import com.beancounter.marketdata.trn.cash.CashServices
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Maps BC compatible delimited data to the domain model
 */
@Service
class BcRowAdapter(
    val assetIngestService: AssetIngestService,
    val cashServices: CashServices,
    val dateUtils: DateUtils = DateUtils(),
) : RowAdapter {
    override fun transform(trustedTrnImportRequest: TrustedTrnImportRequest): TrnInput {
        val tradeDate = dateUtils.getOrThrow(trustedTrnImportRequest.row[colDef()[Columns.Date]!!].trim())
        if (tradeDate.isAfter(LocalDate.now())) {
            throw BusinessException("Cannot accept forward dated trade dates $tradeDate")
        }

        val trnType = TrnType.valueOf(trustedTrnImportRequest.row[colDef()[Columns.Type]!!].trim())
        val marketCode = trustedTrnImportRequest.row[colDef()[Columns.Market]!!].trim()
        val assetCode = trustedTrnImportRequest.row[colDef()[Columns.Code]!!].trim()

        val asset = assetIngestService.resolveAsset(
            marketCode,
            assetCode = assetCode,
            name = trustedTrnImportRequest.row[colDef()[Columns.Name]!!].trim(),
        )
        val cashCurrency = trustedTrnImportRequest.row[colDef()[Columns.CashCurrency]!!].trim()
        val cashAccount = trustedTrnImportRequest.row[colDef()[Columns.CashAccount]!!].trim()
        val cashAssetId = getCashAssetId(trnType, asset, cashAccount, cashCurrency)
        val quantity =
            MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[colDef()[Columns.Quantity]!!]))
        val price =
            MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[colDef()[Columns.Price]!!]))
        val fees = MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[colDef()[Columns.Fees]!!]))
        val tradeBaseRate = MathUtils.parse(trustedTrnImportRequest.row[colDef()[Columns.BaseRate]!!])
        val tradeAmount =
            MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[colDef()[Columns.TradeAmount]!!]))

        return TrnInput(
            callerRef = CallerRef(
                trustedTrnImportRequest.row[0],
                trustedTrnImportRequest.row[1],
                trustedTrnImportRequest.row[2],
            ),
            assetId = asset.id,
            trnType = trnType,
            quantity = quantity,
            tradeCurrency = trustedTrnImportRequest.row[colDef()[Columns.TradeCurrency]!!].trim(),
            tradeBaseRate = tradeBaseRate,
            tradeDate = tradeDate,
            tradeAmount = tradeAmount,
            cashAmount = MathUtils.parse(trustedTrnImportRequest.row[colDef()[Columns.CashAmount]!!]),
            cashCurrency = cashCurrency,
            cashAssetId = cashAssetId,
            fees = fees,
            price = price,
            comments = trustedTrnImportRequest.row[colDef()[Columns.Comments]!!],
        )
    }

    private fun getCashAssetId(
        trnType: TrnType,
        asset: Asset,
        cashAccount: String,
        cashCurrency: String,
    ): String? {
        val cashAsset: Asset? = if (TrnType.isCash(trnType)) {
            asset
        } else {
            cashServices.getCashAsset(trnType, cashAccount, cashCurrency)
        }
        return cashAsset?.id
    }
}
