package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.ingest.RowAdapter
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils
import org.springframework.stereotype.Service

/**
 * Converts BC compatible delimited data to the domain model
 */
@Service
class BcRowAdapter(
    val assetIngestService: AssetIngestService,
    val dateUtils: DateUtils = DateUtils(),
) : RowAdapter {
    override fun transform(trustedTrnImportRequest: TrustedTrnImportRequest): TrnInput {
        val marketCode = trustedTrnImportRequest.row[4].trim()
        val assetCode = trustedTrnImportRequest.row[5].trim()
        val asset = assetIngestService.resolveAsset(
            marketCode,
            assetCode = assetCode,
            name = trustedTrnImportRequest.row[6].trim()
        )
        val trnType = TrnType.valueOf(trustedTrnImportRequest.row[3].trim())
        val quantity = MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[8]))
        val price = MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[10]))
        val fees = MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[11]))
        val tradeAmount = MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[13]))

        return TrnInput(
            trustedTrnImportRequest.callerRef,
            assetId = asset.id,
            trnType = trnType,
            quantity = quantity,
            tradeCurrency = trustedTrnImportRequest.row[9].trim(),
            tradeDate = dateUtils.getOrThrow(trustedTrnImportRequest.row[7]),
            tradeAmount = tradeAmount,
            fees = fees,
            price = price,
            comments = trustedTrnImportRequest.row[14],
        )
    }
}
