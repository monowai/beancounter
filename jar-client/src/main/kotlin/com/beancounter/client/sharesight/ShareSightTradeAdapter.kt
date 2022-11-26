package com.beancounter.client.sharesight

import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.ingest.Filter
import com.beancounter.client.ingest.TrnAdapter
import com.beancounter.client.sharesight.ShareSightConfig.Companion.logFirst
import com.beancounter.common.exception.BusinessException
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
        val ttype = row[type]
        if (ttype.equals("", ignoreCase = true)) {
            throw BusinessException(String.format("Unsupported type %s", row[type]))
        }
        val trnType = TrnType.valueOf(ttype.uppercase(Locale.getDefault()))
        val comment = if (row.size == 13) nullSafe(row[comments]) else null
        var tradeRate: BigDecimal? = null
        var fees = BigDecimal.ZERO
        var tradeAmount = BigDecimal.ZERO
        return try {
            if (trnType !== TrnType.SPLIT) {
                tradeRate = parse(row[fxRate], shareSightConfig.numberFormat)
                fees = calcFees(row, tradeRate)
                tradeAmount = calcTradeAmount(row, tradeRate)
            }
            val asset = resolveAsset(row)
            if (asset == null) {
                log.error("Unable to resolve asset [{}]", row)
                throw BusinessException("Unable to resolve asset [%s]\", row")
            }
            val trnInput = TrnInput(
                CallerRef(trustedTrnImportRequest.portfolio.id, "", row[id]),
                asset.id,
                trnType = trnType,
                quantity = parse(
                    row[quantity],
                    shareSightConfig.numberFormat
                )!!,
                tradeCurrency = row[currency],
                tradeBaseRate = null,
                tradeCashRate = null,
                cashCurrency = trustedTrnImportRequest.portfolio.currency.code,
                tradeDate = dateUtils.getDate(
                    row[date],
                    shareSightConfig.dateFormat,
                    dateUtils.getZoneId()
                ),
                fees = fees,
                price = MathUtils.nullSafe(parse(row[price], shareSightConfig.numberFormat)),
                tradeAmount = tradeAmount,
                comments = comment
            )
            // Zero and null are treated as "unknown"
            trnInput.tradeCashRate = getTradeCashRate(tradeRate)
            trnInput
        } catch (e: ParseException) {
            val message = e.message
            throw logFirst("TRADE", message, row)
        }
    }

    @Throws(ParseException::class)
    private fun calcFees(row: List<String>, tradeRate: BigDecimal?): BigDecimal {
        val result = parse(row[brokerage], shareSightConfig.numberFormat)
        return if (shareSightConfig.isCalculateAmount || result == null) {
            result ?: BigDecimal.ZERO
        } else {
            return divide(result, tradeRate)!!
        }
    }

    private fun getTradeCashRate(tradeRate: BigDecimal?): BigDecimal? {
        return if (shareSightConfig.isCalculateRates || numberUtils.isUnset(tradeRate)) null else tradeRate
    }

    @Throws(ParseException::class)
    private fun calcTradeAmount(row: List<String>, tradeRate: BigDecimal?): BigDecimal {
        var result = parse(row[value], shareSightConfig.numberFormat)
        result = if (shareSightConfig.isCalculateAmount || result == null) {
            val q = BigDecimal(row[quantity])
            val p = BigDecimal(row[price])
            val f =
                MathUtils.nullSafe(MathUtils[row[brokerage]])
            tradeCalculator.amount(q, p, f)
        } else {
            // ShareSight store tradeAmount in portfolio currency, BC stores in Trade CCY
            return multiplyAbs(result, tradeRate)!!
        }
        return result
    }

    private fun nullSafe(o: Any?): String? {
        return if (o?.toString() == "null") {
            null
        } else {
            o?.toString()
        }
    }

    override fun isValid(row: List<String>): Boolean {
        val ttype = row[type]
        return !ttype.contains(".")
    }

    override fun resolveAsset(row: List<String>): Asset? {
        val assetCode = row[code]
        val marketCode = row[market]
        val asset = assetIngestService.resolveAsset(marketCode, assetCode)
        return if (!filter.inFilter(asset)) {
            null
        } else {
            asset
        }
    }

    companion object {
        const val id = 0
        const val market = 1
        const val code = 2
        const val name = 3
        const val type = 4
        const val date = 5
        const val quantity = 6
        const val price = 7
        const val brokerage = 8
        const val currency = 9
        const val fxRate = 10
        const val value = 11
        const val comments = 12
        private val log = LoggerFactory.getLogger(ShareSightTradeAdapter::class.java)
    }
}
