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
        validateTradeDate(trustedTrnImportRequest.row[Columns.Date.ordinal].trim())

        val trnType = TrnType.valueOf(trustedTrnImportRequest.row[Columns.Type.ordinal].trim())
        val asset = resolveAsset(trustedTrnImportRequest)
        val cashAssetId = getCashAssetId(trnType, asset, trustedTrnImportRequest.row)

        return createTrnInput(trustedTrnImportRequest, trnType, asset, cashAssetId)
    }

    private fun validateTradeDate(dateString: String) {
        val tradeDate = dateUtils.getDate(dateString)
        if (tradeDate.isAfter(LocalDate.now())) {
            throw BusinessException("Rejecting the forward dated trade date of $tradeDate")
        }
    }

    private fun resolveAsset(trustedTrnImportRequest: TrustedTrnImportRequest): Asset {
        val marketCode = trustedTrnImportRequest.row[Columns.Market.ordinal].trim()
        val assetCode = trustedTrnImportRequest.row[Columns.Code.ordinal].trim()

        return assetIngestService.resolveAsset(
            AssetInput(
                market = marketCode,
                code = assetCode,
                name = trustedTrnImportRequest.row[Columns.Name.ordinal].trim(),
                owner = trustedTrnImportRequest.portfolio.owner.id
            )
        )
    }

    private fun getCashAssetId(
        trnType: TrnType,
        asset: Asset,
        row: List<String>
    ): String? {
        val cashCurrency = row[Columns.CashCurrency.ordinal].trim()
        val cashAccount = row[Columns.CashAccount.ordinal].trim()

        val cashAsset: Asset? =
            if (TrnType.isCash(trnType)) {
                asset
            } else {
                cashTrnServices.getCashAsset(trnType, cashAccount, cashCurrency)
            }
        return cashAsset?.id
    }

    private fun createTrnInput(
        trustedTrnImportRequest: TrustedTrnImportRequest,
        trnType: TrnType,
        asset: Asset,
        cashAssetId: String?
    ): TrnInput {
        val row = trustedTrnImportRequest.row
        val quantity = MathUtils.nullSafe(MathUtils.parse(row[Columns.Quantity.ordinal]))
        val price = MathUtils.nullSafe(MathUtils.parse(row[Columns.Price.ordinal]))
        val fees = MathUtils.nullSafe(MathUtils.parse(row[Columns.Fees.ordinal]))
        val tradeBaseRate = MathUtils.parse(row[Columns.BaseRate.ordinal])
        val tradeAmount = MathUtils.nullSafe(MathUtils.parse(row[Columns.TradeAmount.ordinal]))
        val tradeCurrency = getTradeCurrency(row, asset)
        val cashCurrency = row[Columns.CashCurrency.ordinal].trim()

        return TrnInput(
            callerRef =
                CallerRef(
                    trustedTrnImportRequest.portfolio.owner.id,
                    row[Columns.Batch.ordinal].trim(),
                    row[Columns.CallerId.ordinal].trim()
                ),
            assetId = asset.id,
            trnType = trnType,
            quantity = quantity,
            tradeCurrency = tradeCurrency,
            tradeBaseRate = tradeBaseRate,
            tradeDate = dateUtils.getDate(row[Columns.Date.ordinal].trim()),
            tradeAmount = tradeAmount,
            cashAmount = MathUtils.parse(row[Columns.CashAmount.ordinal]),
            cashCurrency = cashCurrency,
            cashAssetId = cashAssetId,
            fees = fees,
            price = price,
            comments = row[Columns.Comments.ordinal]
        )
    }

    private fun getTradeCurrency(
        row: List<String>,
        asset: Asset
    ): String =
        if (row[Columns.TradeCurrency.ordinal].isEmpty()) {
            asset.market.currency.code
        } else {
            row[Columns.TradeCurrency.ordinal].trim()
        }
}