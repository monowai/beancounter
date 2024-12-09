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
 *
 * ShareSight amounts are in Portfolio currency; BC expects values in trade currency.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@Service
class ShareSightTradeAdapter(
    private val shareSightConfig: ShareSightConfig,
    private val assetIngestService: AssetIngestService,
    private val dateUtils: DateUtils,
    private val tradeCalculator: TradeCalculator
) : TrnAdapter {
    private val numberUtils = NumberUtils()
    private var filter = Filter(null)

    @Autowired(required = false)
    fun setFilter(filter: Filter) {
        this.filter = filter
    }

    @NonNull
    override fun from(trustedTrnImportRequest: TrustedTrnImportRequest?): TrnInput {
        assert(trustedTrnImportRequest != null)
        val row = trustedTrnImportRequest!!.row
        val ttype = row[TYPE]
        if (ttype.equals(
                "",
                ignoreCase = true
            )
        ) {
            throw BusinessException(
                String.format(
                    "Unsupported type %s",
                    row[TYPE]
                )
            )
        }
        val trnType = TrnType.valueOf(ttype.uppercase(Locale.getDefault()))
        val comment = if (row.size == 13) nullSafe(row[COMMENTS]) else null
        var tradeRate: BigDecimal = BigDecimal.ZERO
        var fees = BigDecimal.ZERO
        var tradeAmount = BigDecimal.ZERO
        return try {
            if (trnType !== TrnType.SPLIT) {
                tradeRate =
                    parse(
                        row[FX_RATE],
                        shareSightConfig.numberFormat
                    )
                fees =
                    calcFees(
                        row,
                        tradeRate
                    )
                tradeAmount =
                    calcTradeAmount(
                        row,
                        tradeRate
                    )
            }
            val asset = resolveAsset(row)
            if (asset == null) {
                log.error(
                    "Unable to resolve asset [{}]",
                    row
                )
                throw BusinessException("Unable to resolve asset [%s]\", row")
            }
            val trnInput =
                TrnInput(
                    CallerRef(
                        trustedTrnImportRequest.portfolio.id,
                        "",
                        row[ID]
                    ),
                    asset.id,
                    trnType = trnType,
                    quantity =
                        parse(
                            row[QUANTITY],
                            shareSightConfig.numberFormat
                        ),
                    tradeCurrency = row[CURRENCY],
                    cashCurrency = trustedTrnImportRequest.portfolio.currency.code,
                    tradeDate =
                        dateUtils.getFormattedDate(
                            row[DATE],
                            listOf(shareSightConfig.dateFormat)
                        ),
                    fees = fees,
                    price =
                        MathUtils.nullSafe(
                            parse(
                                row[PRICE],
                                shareSightConfig.numberFormat
                            )
                        ),
                    tradeAmount = tradeAmount,
                    comments = comment
                )
            // Zero and null are treated as "unknown"
            trnInput.tradeCashRate = getTradeCashRate(tradeRate)
            trnInput
        } catch (e: ParseException) {
            val message = e.message
            throw logFirst(
                "TRADE",
                message,
                row
            )
        }
    }

    @Throws(ParseException::class)
    private fun calcFees(
        row: List<String>,
        tradeRate: BigDecimal?
    ): BigDecimal {
        val result =
            parse(
                row[BROKERAGE],
                shareSightConfig.numberFormat
            )
        return if (shareSightConfig.isCalculateAmount) {
            result
        } else {
            return divide(
                result,
                tradeRate
            )
        }
    }

    private fun getTradeCashRate(tradeRate: BigDecimal): BigDecimal =
        if (shareSightConfig.isCalculateRates || numberUtils.isUnset(tradeRate)) {
            BigDecimal.ZERO
        } else {
            tradeRate
        }

    @Throws(ParseException::class)
    private fun calcTradeAmount(
        row: List<String>,
        tradeRate: BigDecimal?
    ): BigDecimal {
        var result =
            parse(
                row[VALUE],
                shareSightConfig.numberFormat
            )
        result =
            if (shareSightConfig.isCalculateAmount) {
                val q = BigDecimal(row[QUANTITY])
                val p = BigDecimal(row[PRICE])
                val f = MathUtils.nullSafe(MathUtils[row[BROKERAGE]])
                tradeCalculator.amount(
                    q,
                    p,
                    f
                )
            } else {
                // ShareSight store tradeAmount in portfolio currency, BC stores in Trade CCY
                return multiplyAbs(
                    result,
                    tradeRate
                )
            }
        return result
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
                AssetInput(
                    marketCode,
                    assetCode
                )
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