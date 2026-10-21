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

    /** The asset an Asset Review page asks about, or null for any other page or no ticker. */
    fun assetReviewSubject(context: Map<String, Any>?): AssetReviewSubject? {
        val fields = context.orEmpty()
        if (normalise(fields["page"]) != normalise(PageQueries.ASSET_REVIEW)) return null
        val ticker = text(fields, "tickers")
        if (ticker.isEmpty()) return null
        return AssetReviewSubject(ticker.uppercase(), text(fields, "market").uppercase())
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
    }
}