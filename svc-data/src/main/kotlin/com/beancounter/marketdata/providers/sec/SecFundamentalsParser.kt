package com.beancounter.marketdata.providers.sec

import com.beancounter.common.utils.BcJson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pure extraction over the SEC `companyfacts` JSON (`facts.us-gaap.<Tag>.units.<unit>[]`).
 *
 * The snapshot describes one fiscal year. The anchor is the latest `end` across every metric
 * tag's `form == "10-K"` / `fp == "FY"` entries (latest `filed` on a tie - a 10-K restates the
 * prior year as a comparative, so the same period appears under two filings). Each metric is then
 * taken only from its entry at that same period end; a tag the filer did not report for that year
 * is null rather than silently borrowed from an older year. `Revenues` falls back to
 * `RevenueFromContractWithCustomerExcludingAssessedTax` - ASC 606 filers often report only the
 * latter. Shares come from the `dei` instant tag's latest `end`, any form, and must be a whole
 * number that fits a Long. A malformed entry (non-numeric `val`, unparseable `end`) is skipped;
 * only a document with no fiscal-year 10-K entry at all yields null.
 */
@Component
class SecFundamentalsParser(
    private val objectMapper: ObjectMapper = BcJson.objectMapper
) {
    private val log = LoggerFactory.getLogger(SecFundamentalsParser::class.java)

    fun parse(json: String): SecFundamentalsSnapshot? {
        val facts = objectMapper.readTree(json).path("facts")
        val gaap = facts.path("us-gaap")

        val fiscalYears = METRIC_TAGS.associateWith { tag -> fiscalYearEntries(gaap.path(tag)) }
        val anchor =
            fiscalYears.values.flatten().maxWithOrNull(ENTRY_ORDER)
                ?: return null

        fun metricAt(tag: String): BigDecimal? =
            fiscalYears
                .getValue(tag)
                .filter { it.end == anchor.end }
                .maxWithOrNull(ENTRY_ORDER)
                ?.value

        val shares =
            entries(facts.path("dei").path(TAG_SHARES_OUTSTANDING))
                .maxWithOrNull(ENTRY_ORDER)

        return SecFundamentalsSnapshot(
            fiscalYearEnd = anchor.end,
            fiscalYear = anchor.fiscalYear,
            epsDiluted = metricAt(TAG_EPS_DILUTED),
            revenue = metricAt(TAG_REVENUES) ?: metricAt(TAG_REVENUE_FROM_CONTRACT),
            netIncome = metricAt(TAG_NET_INCOME),
            dividendsPerShare = metricAt(TAG_DIVIDENDS_PER_SHARE),
            sharesOutstanding = shares?.let(::wholeShares)
        )
    }

    /** `toLong()` truncates fractions and wraps past Long.MAX_VALUE - both silently corrupt. */
    private fun wholeShares(entry: Entry): Long? =
        runCatching { entry.value.longValueExact() }
            .onFailure {
                log.warn(
                    "Ignoring SEC shares outstanding {} ending {}: not a whole Long",
                    entry.value,
                    entry.end
                )
            }.getOrNull()

    private fun fiscalYearEntries(tag: JsonNode): List<Entry> =
        entries(tag).filter { it.form == FORM_10K && it.period == PERIOD_FY }

    /** Flattens every unit array under `units` - the unit key varies (USD, USD/shares, shares). */
    private fun entries(tag: JsonNode): List<Entry> =
        tag
            .path("units")
            .properties()
            .flatMap { (_, array) -> array.mapNotNull(::toEntry) }

    private fun toEntry(node: JsonNode): Entry? {
        val value = node.path("val")
        val end = runCatching { LocalDate.parse(node.path("end").asString()) }.getOrNull()
        if (!value.isNumber || end == null) {
            return null
        }
        return Entry(
            end = end,
            filed = node.path("filed").asString(),
            fiscalYear = node.path("fy").asInt(),
            period = node.path("fp").asString(),
            form = node.path("form").asString(),
            value = BigDecimal(value.asString())
        )
    }

    private data class Entry(
        val end: LocalDate,
        val filed: String,
        val fiscalYear: Int,
        val period: String,
        val form: String,
        val value: BigDecimal
    )

    companion object {
        const val TAG_EPS_DILUTED = "EarningsPerShareDiluted"
        const val TAG_REVENUES = "Revenues"
        const val TAG_REVENUE_FROM_CONTRACT = "RevenueFromContractWithCustomerExcludingAssessedTax"
        const val TAG_NET_INCOME = "NetIncomeLoss"
        const val TAG_DIVIDENDS_PER_SHARE = "CommonStockDividendsPerShareDeclared"
        const val TAG_SHARES_OUTSTANDING = "EntityCommonStockSharesOutstanding"
        private const val FORM_10K = "10-K"
        private const val PERIOD_FY = "FY"

        private val METRIC_TAGS =
            listOf(TAG_EPS_DILUTED, TAG_REVENUES, TAG_REVENUE_FROM_CONTRACT, TAG_NET_INCOME, TAG_DIVIDENDS_PER_SHARE)
        private val ENTRY_ORDER = compareBy<Entry>({ it.end }, { it.filed })
    }
}