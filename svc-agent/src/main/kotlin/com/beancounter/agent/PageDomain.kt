package com.beancounter.agent

/**
 * The analytical domain a page belongs to: the one page → domain mapping that
 * picks the system prompt ([SystemPromptSelector]), the tool set
 * ([com.beancounter.agent.tools.ToolSelector]) and the model tier
 * ([ChatModelSelector]), so a page never gets one domain's prompt and
 * another's tools or tier.
 *
 * A page is [normalisePage]d, then matched by keyword in declaration order:
 * the first domain whose keyword the page contains wins, so "Holding
 * Analysis" is never read as Wealth and "Asset Review" never as Asset. A
 * page that matches none, or no page at all, is [GENERAL].
 *
 * [smart] domains escalate to the SMART model tier and reason before a
 * canned answer; the rest stay on FAST.
 */
enum class PageDomain(
    val smart: Boolean,
    private val keywords: List<String> = emptyList(),
    private val words: List<String> = emptyList()
) {
    HOLDING_ANALYSIS(smart = true, keywords = listOf("holding analysis")),
    WATCHLIST(smart = true, keywords = listOf("watchlist")),
    ASSET_REVIEW(smart = true, keywords = listOf("asset review")),

    /** Both words, in either order. */
    NEWS_SENTIMENT(smart = false) {
        override fun matches(page: String): Boolean = "news" in page && "sentiment" in page
    },

    /** "fi" (financial independence) counts as a whole word only: "Profile" is not a plan. */
    INDEPENDENCE(smart = true, keywords = listOf("independence", "retire"), words = listOf("fi")),
    REBALANCE(smart = true, keywords = listOf("rebalanc")),
    ASSET(smart = false, keywords = listOf("event", "proposed", "dividend", "trade", "trn")),
    WEALTH(smart = false, keywords = listOf("holding", "portfolio", "wealth", "position")),
    GENERAL(smart = false);

    /** Whether the [normalisePage]d [page] belongs to this domain. */
    protected open fun matches(page: String): Boolean =
        keywords.any { it in page } || (words.isNotEmpty() && page.split(' ').any { it in words })

    companion object {
        /** The domain of the context's `page`. */
        fun of(context: Map<String, Any>?): PageDomain = forPage(context?.get("page"))

        /** The domain of [page], as any client spells it. */
        fun forPage(page: Any?): PageDomain {
            val normalised = normalisePage(page)
            return entries.firstOrNull { it != GENERAL && it.matches(normalised) } ?: GENERAL
        }
    }
}

/**
 * A page with a canned question, matched exactly ([normalisePage] aside), not
 * by keyword like [PageDomain]: "Asset Review notes" is in the Asset Review
 * domain but is not an Asset Review page. This is deliberate. A blank query
 * on a merely similar page is a 400, not a canned answer, and the shared
 * Asset Review cache must not canonicalise a near-miss page into a review
 * every caller is served.
 */
enum class CannedPage(
    /** The page name a canonical context carries. */
    val page: String,
    /** The domain this page resolves to, so a canned page routes like any other. */
    val domain: PageDomain
) {
    ASSET_REVIEW(PageQueries.ASSET_REVIEW, PageDomain.ASSET_REVIEW),
    HOLDING_ANALYSIS(PageQueries.HOLDING_ANALYSIS, PageDomain.HOLDING_ANALYSIS),
    PORTFOLIO_REVIEW(PageQueries.PORTFOLIO_REVIEW, PageDomain.WEALTH);

    private val normalised = normalisePage(page)

    companion object {
        /** The canned page the context's `page` names exactly, or null for any other page. */
        fun of(context: Map<String, Any>?): CannedPage? {
            val page = normalisePage(context?.get("page"))
            return entries.firstOrNull { it.normalised == page }
        }
    }
}

private val WHITESPACE = Regex("\\s+")

/**
 * A page as every matcher compares it: trimmed, lower-cased, hyphens read as
 * spaces and each run of whitespace as one space, so "Asset-Review" and
 * " asset  review " are the same page.
 */
fun normalisePage(page: Any?): String =
    page
        ?.toString()
        .orEmpty()
        .trim()
        .lowercase()
        .replace('-', ' ')
        .replace(WHITESPACE, " ")