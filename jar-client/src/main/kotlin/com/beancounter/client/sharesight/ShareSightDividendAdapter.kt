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
import com.beancounter.common.utils.MathUtils.Companion.multiplyAbs
import com.beancounter.common.utils.MathUtils.Companion.parse
import com.beancounter.common.utils.NumberUtils
import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.lang.NonNull
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.text.ParseException
import java.util.Locale

/**
 * Converts from the ShareSight dividend format.
 *
 *
 * ShareSight amounts are in Portfolio currency; BC expects values in trade currency.
 *
 * @author mikeh
 * @since 2019-02-08
 */
@Service
class ShareSightDividendAdapter(
    private val shareSightConfig: ShareSightConfig,
    private val assetIngestService: AssetIngestService
) : TrnAdapter {
    private val dateUtils = DateUtils()
    private var filter = Filter(null)
    private val numberUtils = NumberUtils()

    @Autowired(required = false)
    fun setFilter(filter: Filter) {
        this.filter = filter
    }

    @NonNull
    override fun from(trustedTrnImportRequest: TrustedTrnImportRequest?): TrnInput {
        assert(trustedTrnImportRequest != null)
        val row = trustedTrnImportRequest!!.row
        return try {
            val asset = resolveAsset(row)
            val tradeRate = parse(row[fxRate], shareSightConfig.numberFormat)
            val trnInput = TrnInput(
                CallerRef(trustedTrnImportRequest.portfolio.id, callerId = row[id]),
                asset.id,
                trnType = TrnType.DIVI,
                quantity = BigDecimal.ZERO,
                tradeCurrency = row[currency],
                tradeBaseRate = null,
                tradeCashRate = null,
                tradeDate = dateUtils.getDate(
                    row[date],
                    shareSightConfig.dateFormat,
                    dateUtils.getZoneId()
                ),
                fees = BigDecimal.ZERO,
                price = BigDecimal.ZERO,
                tradeAmount = multiplyAbs(
                    parse(
                        row[net],
                        shareSightConfig.numberFormat
                    ),
                    tradeRate
                )!!,
                tax = multiplyAbs(BigDecimal(row[tax]), tradeRate)!!,
                cashAmount = multiplyAbs(
                    parse(row[net], shareSightConfig.numberFormat),
                    tradeRate
                ),
                comments = row[comments]
            )
            trnInput.tradeCashRate = if (shareSightConfig.isCalculateRates || numberUtils.isUnset(tradeRate)
            ) null else tradeRate
            trnInput // Result!
        } catch (e: NumberFormatException) {
            val message = e.message
            throw logFirst("DIVI", message, row)
        } catch (e: ParseException) {
            val message = e.message
            throw logFirst("DIVI", message, row)
        }
    }

    override fun isValid(row: List<String>): Boolean {
        val rate = row[fxRate].uppercase(Locale.getDefault())
        return rate.contains(".") // divis have an fx rate in this column
    }

    override fun resolveAsset(row: List<String>): Asset {
        val values = parseAsset(row[code])
        return assetIngestService.resolveAsset(
            values[1].uppercase(Locale.getDefault()), values[0]
        )
    }

    private fun parseAsset(input: String?): List<String> {
        if (input == null || input.isEmpty()) {
            throw BusinessException("Unable to resolve Asset code")
        }
        val values = Splitter
            .on(CharMatcher.anyOf(".:-"))
            .trimResults()
            .splitToList(input)
        if (values.isEmpty() || values[0] == input) {
            throw BusinessException(String.format("Unable to parse %s", input))
        }
        return values
    }

    companion object {
        const val id = 0
        const val code = 1
        const val name = 2
        const val date = 3
        const val fxRate = 4
        const val currency = 5
        const val net = 6
        const val tax = 7
        const val gross = 8
        const val comments = 9
    }
}
