package com.beancounter.agent

import com.beancounter.agent.conversation.ConversationKind
import com.beancounter.common.exception.BusinessException
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
 * The holding a Holding Analysis is about: an asset, as an Asset Review names
 * it, in one of the caller's portfolios. Upper-cased; [market] is blank when
 * the page sent none.
 */
data class HoldingAnalysisSubject(
    val ticker: String,
    val market: String,
    val portfolioCode: String
) {
    /** The asset alone, which is what its Asset Review is about. */
    val asset: AssetReviewSubject = AssetReviewSubject(ticker, market)
}

/**
 * Maps a page context to its canned user question. A caller that sends no
 * question of its own is asked this one. Pages match as a [CannedPage]:
 * exactly (case, hyphens and spacing aside), so "Asset Review notes" is in
 * the Asset Review [PageDomain] but is not an Asset Review page.
 *
 * See [PageQueries] for the question text.
 */
@Service
class PageQuerySelector {
    fun queryFor(context: Map<String, Any>?): CannedQuery? {
        val fields = context.orEmpty()
        return when (CannedPage.of(fields)) {
            CannedPage.ASSET_REVIEW -> assetReview(fields)
            CannedPage.PORTFOLIO_REVIEW -> CannedQuery(PageQueries.PORTFOLIO_REVIEW, PageQueries.DAILY_BRIEFING)
            CannedPage.HOLDING_ANALYSIS, null -> null
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
        if (CannedPage.of(fields) != CannedPage.ASSET_REVIEW) return null
        // Text only: a non-string market would route differently once rebuilt as text (isFxPair).
        val rawTicker = fields["tickers"] as? String ?: return null
        val rawMarket = fields["market"] ?: ""
        if (rawMarket !is String) return null
        val ticker = rawTicker.trim().uppercase()
        val market = rawMarket.trim().uppercase()
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

    /**
     * The holding a Holding Analysis page asks about, or null for any other
     * page. Its ticker and market follow the Asset Review subject's rules and
     * its portfolio code must be a plain code: anything else on the page is a
     * [BusinessException] (400) carrying [INVALID_HOLDING_ANALYSIS]. A
     * currency pair is refused the same way: its review is the FX review, with
     * other tools, and a pair is not a holding to analyse.
     */
    fun holdingAnalysisSubject(context: Map<String, Any>?): HoldingAnalysisSubject? {
        val fields = context.orEmpty()
        if (CannedPage.of(fields) != CannedPage.HOLDING_ANALYSIS) return null
        val asset =
            assetReviewSubject(fields + ("page" to PageQueries.ASSET_REVIEW))
                ?: throw BusinessException(INVALID_HOLDING_ANALYSIS)
        if (isFxPair(mapOf("market" to asset.market))) throw BusinessException(INVALID_HOLDING_ANALYSIS)
        val portfolioCode =
            (fields["portfolioCode"] as? String)
                ?.trim()
                ?.uppercase()
                ?.takeIf { PORTFOLIO.matches(it) }
                ?: throw BusinessException(INVALID_HOLDING_ANALYSIS)
        return HoldingAnalysisSubject(asset.ticker, asset.market, portfolioCode)
    }

    /** The canonical page context for [subject], so nothing else a caller sent reaches the prompt. */
    fun holdingAnalysisContext(subject: HoldingAnalysisSubject): Map<String, Any> =
        buildMap {
            put("page", PageQueries.HOLDING_ANALYSIS)
            put("tickers", subject.ticker)
            if (subject.market.isNotEmpty()) put("market", subject.market)
            put("portfolioCode", subject.portfolioCode)
        }

    /** The kind of conversation a first turn on this page starts. */
    fun kindFor(context: Map<String, Any>?): ConversationKind =
        when (CannedPage.of(context)) {
            CannedPage.ASSET_REVIEW -> ConversationKind.ASSET_REVIEW
            CannedPage.HOLDING_ANALYSIS -> ConversationKind.HOLDING_ANALYSIS
            CannedPage.PORTFOLIO_REVIEW, null -> ConversationKind.CHAT
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

    companion object {
        /** The 400 a malformed Holding Analysis request gets. A contract: clients key off it. */
        const val INVALID_HOLDING_ANALYSIS = "invalid-holding-analysis"

        /** The 400 a Holding Analysis asked inside an Asset Review conversation gets. Also a contract. */
        const val HOLDING_IN_ASSET_REVIEW = "holding-analysis-in-asset-review"

        // Listing symbols as markets quote them: BRK.B, BRK-B, ^GSPC (caret leads only), NZDSGD, D05.
        private val TICKER = Regex("^\\^?[A-Z0-9][A-Z0-9.\\-]{0,31}$")

        // Market codes: US, NASDAQ, SGX, FX, INDEX — or none sent.
        private val MARKET = Regex("^[A-Z0-9_\\-]{0,16}$")

        // Portfolio codes as svc-data stores them, upper-cased: TYLER, SG-CPF, USD_2.
        private val PORTFOLIO = Regex("^[A-Z0-9][A-Z0-9_.\\-]{0,63}$")
    }
}