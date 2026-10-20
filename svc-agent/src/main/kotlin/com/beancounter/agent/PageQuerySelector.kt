package com.beancounter.agent

import org.springframework.stereotype.Service

/** A page's canned question and the label a saved conversation shows for it. */
data class CannedQuery(
    val label: String,
    val question: String
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