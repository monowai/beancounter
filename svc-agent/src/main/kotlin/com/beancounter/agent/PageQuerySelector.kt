package com.beancounter.agent

import org.springframework.stereotype.Service

/** A page's canned question and the label a saved conversation shows for it. */
data class CannedQuery(
    val label: String,
    val question: String
)

/** What a canned Asset Review is about, upper-cased; [market] is blank when the page sent none. */
data class AssetReviewSubject(
    val ticker: String,
    val market: String
)

/**
 * Maps a page context to its canned user question, the way
 * [SystemPromptSelector] maps it to the system prompt. A caller that sends no
 * question of its own is asked this one. Pages match exactly (case, hyphens
 * and spacing aside), so "Asset Review notes" is not an Asset Review.
 *
 * See [PageQueries] for the question text.
 */
@Service
class PageQuerySelector {
    fun queryFor(context: Map<String, Any>?): CannedQuery? {
        val fields = context.orEmpty()
        return when (normalise(fields["page"])) {
            normalise(PageQueries.ASSET_REVIEW) -> assetReview(fields)
            normalise(
                PageQueries.PORTFOLIO_REVIEW
            ) -> CannedQuery(PageQueries.PORTFOLIO_REVIEW, PageQueries.DAILY_BRIEFING)
            else -> null
        }
    }

    /**
     * The asset an Asset Review page asks about, or null for any other page,
     * no ticker, or a ticker or market that is not a plain symbol. A review
     * of a subject is shared by every caller, so only a value that cannot
     * carry instructions qualifies.
     */
    fun assetReviewSubject(context: Map<String, Any>?): AssetReviewSubject? {
        val fields = context.orEmpty()
        if (normalise(fields["page"]) != normalise(PageQueries.ASSET_REVIEW)) return null
        val ticker = text(fields, "tickers").uppercase()
        val market = text(fields, "market").uppercase()
        if (!TICKER.matches(ticker) || !MARKET.matches(market)) return null
        return AssetReviewSubject(ticker, market)
    }

    /**
     * The canonical page context for [subject]: page, ticker and market only,
     * so nothing a caller sent beyond the cache key reaches a shared prompt.
     */
    fun assetReviewContext(subject: AssetReviewSubject): Map<String, Any> =
        buildMap {
            put("page", PageQueries.ASSET_REVIEW)
            put("tickers", subject.ticker)
            if (subject.market.isNotEmpty()) put("market", subject.market)
        }

    private fun assetReview(fields: Map<String, Any>): CannedQuery? {
        val ticker = text(fields, "tickers")
        if (ticker.isEmpty()) return null
        return CannedQuery(
            PageQueries.ASSET_REVIEW,
            PageQueries.assetReview(ticker, text(fields, "assetName"), text(fields, "market"))
        )
    }

    private fun text(
        fields: Map<String, Any>,
        key: String
    ): String = fields[key]?.toString().orEmpty().trim()

    private fun normalise(page: Any?): String =
        page
            ?.toString()
            .orEmpty()
            .trim()
            .lowercase()
            .replace('-', ' ')
            .replace(WHITESPACE, " ")

    private companion object {
        val WHITESPACE = Regex("\\s+")

        // Listing symbols as markets quote them: BRK.B, ^GSPC, NZDSGD, D05.
        val TICKER = Regex("^[A-Z0-9^][A-Z0-9.\\-^=:]{0,31}$")

        // Market codes: US, NASDAQ, SGX, FX, INDEX — or none sent.
        val MARKET = Regex("^[A-Z0-9_\\-]{0,16}$")
    }
}