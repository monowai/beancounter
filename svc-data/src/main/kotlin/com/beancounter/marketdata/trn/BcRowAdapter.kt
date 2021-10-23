package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.ingest.RowAdapter
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils
import com.beancounter.marketdata.trn.TrnIoDefinition.Companion.colDef
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Converts BC compatible delimited data to the domain model
 */
@Service
class BcRowAdapter(
    val assetIngestService: AssetIngestService,
    val dateUtils: DateUtils = DateUtils(),
) : RowAdapter {
    override fun transform(trustedTrnImportRequest: TrustedTrnImportRequest): TrnInput {
        val marketCode = trustedTrnImportRequest.row[colDef()[TrnIoDefinition.Columns.Market]!!].trim()
        val assetCode = trustedTrnImportRequest.row[colDef()[TrnIoDefinition.Columns.Code]!!].trim()
        val asset = assetIngestService.resolveAsset(
            marketCode,
            assetCode = assetCode,
            name = trustedTrnImportRequest.row[colDef()[TrnIoDefinition.Columns.Name]!!].trim()
        )
        val trnType = TrnType.valueOf(trustedTrnImportRequest.row[colDef()[TrnIoDefinition.Columns.Type]!!].trim())
        val quantity =
            MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[colDef()[TrnIoDefinition.Columns.Quantity]!!]))
        val price =
            MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[colDef()[TrnIoDefinition.Columns.Price]!!]))
        val fees = MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[colDef()[TrnIoDefinition.Columns.Fees]!!]))
        val tradeBaseRate = MathUtils.parse(trustedTrnImportRequest.row[colDef()[TrnIoDefinition.Columns.BaseRate]!!])
        val tradeAmount =
            MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[colDef()[TrnIoDefinition.Columns.TradeAmount]!!]))
        val tradeDate = dateUtils.getOrThrow(trustedTrnImportRequest.row[colDef()[TrnIoDefinition.Columns.Date]!!])

        if (tradeDate.isAfter(LocalDate.now())) {
            throw BusinessException("Cannot accept forward dated trade dates $tradeDate")
        }

        return TrnInput(
            trustedTrnImportRequest.callerRef,
            assetId = asset.id,
            trnType = trnType,
            quantity = quantity,
            tradeCurrency = trustedTrnImportRequest.row[colDef()[TrnIoDefinition.Columns.TradeCurrency]!!].trim(),
            tradeBaseRate = tradeBaseRate,
            tradeDate = tradeDate,
            tradeAmount = tradeAmount,
            fees = fees,
            price = price,
            comments = trustedTrnImportRequest.row[colDef()[TrnIoDefinition.Columns.Comments]!!],
        )
    }
}
