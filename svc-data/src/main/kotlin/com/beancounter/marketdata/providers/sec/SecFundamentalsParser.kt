package com.beancounter.marketdata.providers.sec

import com.beancounter.common.utils.BcJson
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Pure extraction over the SEC `companyfacts` JSON (`facts.us-gaap.<Tag>.units.<unit>[]`).
 *
 * Rule per duration tag: keep entries with `form == "10-K"` and `fp == "FY"`, pick the latest
 * `end`, and on a tie the latest `filed` (a 10-K restates the prior year as a comparative, so
 * the same period appears under two filings). `Revenues` falls back to
 * `RevenueFromContractWithCustomerExcludingAssessedTax` - ASC 606 filers often report only the
 * latter. Shares come from the `dei` instant tag's latest `end`, any form. A missing tag is a
 * null field, never a failure; only a document with no fiscal-year 10-K entry at all yields null.
 */
@Component
class SecFundamentalsParser(
    private val objectMapper: ObjectMapper = BcJson.objectMapper
) {
    fun parse(json: String): SecFundamentalsSnapshot? {
        val facts = objectMapper.readTree(json).path("facts")
        val gaap = facts.path("us-gaap")

        val eps = latestFiscalYear(gaap.path(TAG_EPS_DILUTED))
        val revenue =
            latestFiscalYear(gaap.path(TAG_REVENUES))
                ?: latestFiscalYear(gaap.path(TAG_REVENUE_FROM_CONTRACT))
        val netIncome = latestFiscalYear(gaap.path(TAG_NET_INCOME))
        val dividends = latestFiscalYear(gaap.path(TAG_DIVIDENDS_PER_SHARE))

        val anchor =
            listOfNotNull(eps, revenue, netIncome, dividends).maxWithOrNull(ENTRY_ORDER)
                ?: return null

        val shares =
            entries(facts.path("dei").path(TAG_SHARES_OUTSTANDING))
                .maxWithOrNull(ENTRY_ORDER)

        return SecFundamentalsSnapshot(
            fiscalYearEnd = anchor.end,
            fiscalYear = anchor.fiscalYear,
            epsDiluted = eps?.value,
            revenue = revenue?.value,
            netIncome = netIncome?.value,
            dividendsPerShare = dividends?.value,
            sharesOutstanding = shares?.value?.toLong()
        )
    }

    private fun latestFiscalYear(tag: JsonNode): Entry? =
        entries(tag)
            .filter { it.form == FORM_10K && it.period == PERIOD_FY }
            .maxWithOrNull(ENTRY_ORDER)

    /** Flattens every unit array under `units` - the unit key varies (USD, USD/shares, shares). */
    private fun entries(tag: JsonNode): List<Entry> =
        tag
            .path("units")
            .properties()
            .flatMap { (_, array) -> array.mapNotNull(::toEntry) }

    private fun toEntry(node: JsonNode): Entry? {
        val value = node.path("val")
        val end = node.path("end").asString()
        if (!value.isNumber || end.isBlank()) {
            return null
        }
        return Entry(
            end = LocalDate.parse(end),
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

        private val ENTRY_ORDER = compareBy<Entry>({ it.end }, { it.filed })
    }
}