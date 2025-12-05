package com.beancounter.client.sharesight

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.ingest.Filter
import com.beancounter.client.ingest.TrnAdapter
import com.beancounter.client.sharesight.ShareSightConfig.Companion.logFirst
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MathUtils
import com.beancounter.common.utils.MathUtils.Companion.divide
import com.beancounter.common.utils.MathUtils.Companion.multiplyAbs
import com.beancounter.common.utils.MathUtils.Companion.parse
import com.beancounter.common.utils.NumberUtils
import com.beancounter.common.utils.TradeCalculator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.lang.NonNull
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.text.ParseException
import java.util.Locale

/**
 * Converts from the ShareSight trade format.
 *
 * ShareSight amounts are in Portfolio currency; BC expects values in trade currency.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@Service
class ShareSightTradeAdapter(
    shareSightConfig: ShareSightConfig,
    private val assetIngestService: AssetIngestService,
    dateUtils: DateUtils,
    tradeCalculator: TradeCalculator
) : TrnAdapter {
    private val numberUtils = NumberUtils()
    private var filter = Filter(null)

    // Delegate to specialized helpers
    private val tradeValueCalculator = TradeValueCalculator(shareSightConfig, tradeCalculator, numberUtils)
    private val trnInputBuilder = TrnInputBuilder(shareSightConfig, dateUtils)

    @Autowired(required = false)
    fun setFilter(filter: Filter) {
        this.filter = filter
    }

    @NonNull
    override fun from(trustedTrnImportRequest: TrustedTrnImportRequest?): TrnInput {
        assert(trustedTrnImportRequest != null)
        val row = trustedTrnImportRequest!!.row

        val trnType = validateAndParseTrnType(row)
        val comment = extractComment(row)

        return try {
            val (tradeRate, fees, tradeAmount) = tradeValueCalculator.calculateTradeValues(row, trnType)
            val asset = resolveAssetSafely(row)
            val trnInput =
                trnInputBuilder.createTrnInput(
                    TrnInputBuilder.TrnInputParams(
                        trustedTrnImportRequest,
                        row,
                        trnType,
                        comment,
                        fees,
                        tradeAmount,
                        asset.id
                    )
                )

            // Zero and null are treated as "unknown"
            trnInput.tradeCashRate = tradeValueCalculator.getTradeCashRate(tradeRate)
            trnInput
        } catch (e: ParseException) {
            val message = e.message!!
            throw logFirst("TRADE", message, row)
        }
    }

    private fun validateAndParseTrnType(row: List<String>): TrnType {
        val ttype = row[TYPE]
        if (ttype.equals("", ignoreCase = true)) {
            throw BusinessException(String.format("Unsupported type %s", row[TYPE]))
        }
        return TrnType.valueOf(ttype.uppercase(Locale.getDefault()))
    }

    private fun extractComment(row: List<String>): String? = if (row.size == 13) nullSafe(row[COMMENTS]) else null

    private fun resolveAssetSafely(row: List<String>): Asset {
        val asset = resolveAsset(row)
        if (asset == null) {
            log.error("Unable to resolve asset [{}]", row)
            throw BusinessException("Unable to resolve asset [$row]")
        }
        return asset
    }

    private fun nullSafe(o: Any?): String? =
        if (o?.toString() == "null") {
            null
        } else {
            o?.toString()
        }

    override fun isValid(row: List<String>): Boolean {
        val ttype = row[TYPE]
        return !ttype.contains(".")
    }

    override fun resolveAsset(row: List<String>): Asset? {
        val assetCode = row[CODE]
        val marketCode = row[MARKET]
        val asset =
            assetIngestService.resolveAsset(
                AssetInput(marketCode, assetCode)
            )
        return if (!filter.inFilter(asset)) {
            null
        } else {
            asset
        }
    }

    companion object {
        const val ID = 0
        const val MARKET = 1
        const val CODE = 2
        const val NAME = 3
        const val TYPE = 4
        const val DATE = 5
        const val QUANTITY = 6
        const val PRICE = 7
        const val BROKERAGE = 8
        const val CURRENCY = 9
        const val FX_RATE = 10
        const val VALUE = 11
        const val COMMENTS = 12
        private val log = LoggerFactory.getLogger(ShareSightTradeAdapter::class.java)
    }
}

/**
 * Handles trade value calculations.
 */
private class TradeValueCalculator(
    private val shareSightConfig: ShareSightConfig,
    private val tradeCalculator: TradeCalculator,
    private val numberUtils: NumberUtils
) {
    fun calculateTradeValues(
        row: List<String>,
        trnType: TrnType
    ): Triple<BigDecimal, BigDecimal, BigDecimal> {
        if (trnType == TrnType.SPLIT) {
            return Triple(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        }

        val tradeRate = parse(row[ShareSightTradeAdapter.FX_RATE], shareSightConfig.numberFormat)
        val fees = calcFees(row, tradeRate)
        val tradeAmount = calcTradeAmount(row, tradeRate)

        return Triple(tradeRate, fees, tradeAmount)
    }

    private fun calcFees(
        row: List<String>,
        tradeRate: BigDecimal?
    ): BigDecimal {
        val result = parse(row[ShareSightTradeAdapter.BROKERAGE], shareSightConfig.numberFormat)
        return if (shareSightConfig.isCalculateAmount) {
            result
        } else {
            divide(result, tradeRate)
        }
    }

    private fun calcTradeAmount(
        row: List<String>,
        tradeRate: BigDecimal?
    ): BigDecimal {
        var result = parse(row[ShareSightTradeAdapter.VALUE], shareSightConfig.numberFormat)
        result =
            if (shareSightConfig.isCalculateAmount) {
                val q = BigDecimal(row[ShareSightTradeAdapter.QUANTITY])
                val p = BigDecimal(row[ShareSightTradeAdapter.PRICE])
                val f = MathUtils.nullSafe(MathUtils[row[ShareSightTradeAdapter.BROKERAGE]])
                tradeCalculator.amount(q, p, f)
            } else {
                // ShareSight store tradeAmount in portfolio currency, BC stores in Trade CCY
                multiplyAbs(result, tradeRate)
            }
        return result
    }

    fun getTradeCashRate(tradeRate: BigDecimal): BigDecimal =
        if (shareSightConfig.isCalculateRates || numberUtils.isUnset(tradeRate)) {
            BigDecimal.ZERO
        } else {
            tradeRate
        }
}

/**
 * Handles TrnInput creation.
 */
private class TrnInputBuilder(
    private val shareSightConfig: ShareSightConfig,
    private val dateUtils: DateUtils
) {
    /**
     * Data class to encapsulate parameters for TrnInput creation.
     */
    data class TrnInputParams(
        val trustedTrnImportRequest: TrustedTrnImportRequest,
        val row: List<String>,
        val trnType: TrnType,
        val comment: String?,
        val fees: BigDecimal,
        val tradeAmount: BigDecimal,
        val assetId: String
    )

    fun createTrnInput(params: TrnInputParams): TrnInput =
        TrnInput(
            CallerRef(
                params.trustedTrnImportRequest.portfolio.id,
                "",
                params.row[ShareSightTradeAdapter.ID]
            ),
            params.assetId,
            trnType = params.trnType,
            quantity = parse(params.row[ShareSightTradeAdapter.QUANTITY], shareSightConfig.numberFormat),
            tradeCurrency = params.row[ShareSightTradeAdapter.CURRENCY],
            cashCurrency = params.trustedTrnImportRequest.portfolio.currency.code,
            tradeDate =
                dateUtils.getFormattedDate(
                    params.row[ShareSightTradeAdapter.DATE],
                    listOf(shareSightConfig.dateFormat)
                ),
            fees = params.fees,
            price = MathUtils.nullSafe(parse(params.row[ShareSightTradeAdapter.PRICE], shareSightConfig.numberFormat)),
            tradeAmount = params.tradeAmount,
            comments = params.comment
        )
}