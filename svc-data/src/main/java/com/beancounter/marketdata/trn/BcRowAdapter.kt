package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.ingest.RowAdapter
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils
import org.springframework.stereotype.Service
import java.text.NumberFormat

@Service
class BcRowAdapter(val assetIngestService: AssetIngestService, val dateUtils: DateUtils) : RowAdapter {
    override fun transform(trustedTrnImportRequest: TrustedTrnImportRequest): TrnInput {
        val marketCode = trustedTrnImportRequest.row[4]
        val assetCode = trustedTrnImportRequest.row[5]
        val quantity = MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[8], NumberFormat.getInstance()))
        val asset = assetIngestService.resolveAsset(marketCode, assetCode)
        return TrnInput(
            trustedTrnImportRequest.callerRef,
            assetId = asset.id,
            trnType = TrnType.valueOf(trustedTrnImportRequest.row[3]),
            quantity = quantity,
            tradeCurrency = trustedTrnImportRequest.row[9],
            tradeDate = dateUtils.getOrThrow(trustedTrnImportRequest.row[7]),
            fees = MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[11], NumberFormat.getInstance())),
            price = MathUtils.nullSafe(MathUtils.parse(trustedTrnImportRequest.row[8], NumberFormat.getInstance())),
            comments = trustedTrnImportRequest.row[14],
        )
    }
}
