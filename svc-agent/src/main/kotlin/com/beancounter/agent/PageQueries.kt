package com.beancounter.agent

import com.beancounter.agent.holding.HoldingFacts
import java.time.LocalDate

/**
 * The canned user questions behind a page's one-tap analysis. A client names
 * the page and its subject in `context`; the question itself lives here, so a
 * change to it reaches every client at once. [PageQuerySelector] picks one.
 */
object PageQueries {
    const val ASSET_REVIEW = "Asset Review"
    const val PORTFOLIO_REVIEW = "Portfolio Review"
    const val HOLDING_ANALYSIS = "Holding Analysis"

    /** What a Holding Analysis is stored under when the caller gave no label. */
    fun holdingAnalysisLabel(ticker: String): String = "$HOLDING_ANALYSIS — $ticker"

    /**
     * The opening question of a Holding Analysis: the Asset Review of the
     * asset, labelled with its date, then the caller's holding as
     * svc-position reports it, then what to make of the two together.
     */
    fun holdingAnalysis(
        subject: HoldingAnalysisSubject,
        reviewDate: LocalDate,
        review: String,
        facts: HoldingFacts
    ): String {
        val marketLabel = if (subject.market.isBlank()) "" else " listed on ${subject.market}"
        return listOf(
            "Asset Review of ${subject.ticker}$marketLabel, as of $reviewDate:",
            "<$REVIEW_TAG>",
            unfenced(review.trim()),
            "</$REVIEW_TAG>",
            "",
            "The user's holding (figures from their portfolio records; cite them, never recompute them):",
            "<$HOLDING_TAG>",
            facts.describe(),
            "</$HOLDING_TAG>",
            "",
            HOLDING_ANALYSIS_QUESTION
        ).joinToString("\n")
    }

    private const val REVIEW_TAG = "asset-review"
    private const val HOLDING_TAG = "holding"
    private val FENCE = Regex("<\\s*/?\\s*($REVIEW_TAG|$HOLDING_TAG)\\s*>", RegexOption.IGNORE_CASE)

    /**
     * [text] with every opening or closing fence tag taken out, so text
     * inside a fence (a shared review, an asset's name) cannot close it and
     * speak outside it.
     */
    fun unfenced(text: String): String = FENCE.replace(text, "")

    private const val HOLDING_ANALYSIS_QUESTION =
        "Produce a Holding Analysis: assess this position in light of the Asset Review above. Cover " +
            "position size (its weight in the portfolio against what the review says about the asset), " +
            "entry (average cost against the current price), the result so far (the unrealised gain on " +
            "cost), what the review's points mean for this holding, and what to watch. Stay factual and " +
            "use the figures given. No recommendations to buy or sell."

    /**
     * A single-asset deep dive. The name and market are left out when the
     * page did not send them, as bc-view does.
     */
    fun assetReview(
        ticker: String,
        assetName: String,
        market: String
    ): String {
        val nameLabel = if (assetName.isBlank()) "" else " ($assetName)"
        val marketLabel = if (market.isBlank()) "" else " listed on $market"
        return "Produce an Asset Review for $ticker$nameLabel$marketLabel. " +
            "Cover company and sector context, current sentiment from recent news, " +
            "corporate-action history (dividends and splits in the last 12 months), " +
            "and qualitative risk callouts. Stay at the ticker level — do not assume " +
            "the user holds it."
    }

    private const val TAB = "\t"

    /**
     * The daily portfolio briefing: a columnist's 250–400 word morning read.
     * It makes the model classify the book (equity / fixed income / mixed /
     * cash) before writing and use that class's vocabulary and yardstick, so a
     * bond sleeve is not read as an equity bet. Section order is fixed:
     * headline, drivers, patterns, longer arc (XIRR against the day), what to
     * watch.
     */
    val DAILY_BRIEFING =
        """
        Role: You are a financial columnist writing a daily portfolio briefing. Your tone is that of a seasoned markets writer — informed, measured, and contextual. You explain why things moved, not just what moved.

        Inputs available to you:
        $TAB• Portfolio holdings with weights — equities, ETFs, bond funds, cash. Infer each holding's asset class from its name; the category field is coarse (every ETF is just "MUTUAL FUND") and cannot tell a bond fund from an equity fund.
        $TAB• Day's return per holding.
        $TAB• XIRR (portfolio and per-holding). It is annualised, so it exaggerates in both directions for holdings under ~6 months old.
        $TAB• Macro backdrop over a lookback window (treasury yield changes in bps, oil), rate-decision odds, and a news feed for holdings and broader markets.
        $TAB• You have NO duration, yield-to-maturity, credit-quality, or sector data. Where you infer these from fund names, say it is an inference and give a range, never a point figure.

        Before writing, classify the book: equity, fixed income, mixed, or cash. Then use that class's vocabulary and yardstick:
        $TAB• Equity book — sectors, themes, factor tilts; compare against the broad market and the dominant sector benchmarks.
        $TAB• Fixed-income book — duration, rates versus credit spread, curve shape. Compare against yield moves, not equity indices. Judge diversification within the mandate: a bond fund that holds only bonds is not "undiversified" — distinguish rates exposure (treasuries, aggregate) from credit exposure (high yield, emerging market) and short-duration ballast.
        $TAB• Mixed book — treat each sleeve by its own rules, then say how they offset.

        Your brief should cover, in this order:
        ${TAB}1. The headline — One or two sentences. How did the portfolio do today, and is that consistent with or divergent from the yardstick for its class? Lead with the number, then the context.
        ${TAB}2. What drove it — Identify the 2–4 holdings (or sectors/themes/factors) that contributed most to the day's move, positive or negative. Tie movements to news or macro factors only where the data supports it on the same day. Match windows: a multi-week yield or oil move is backdrop, never the cause of a single day's return. If nothing in the data explains today's move, say so.
        ${TAB}3. Patterns and themes — Step back. Are the day's moves part of a pattern (rate-sensitive names moving together, a rotation into defensives, one factor dominating)? Is the portfolio behaving as a coherent set of bets or as uncorrelated noise? Concentration is judged against the mandate, not against an all-asset ideal.
        ${TAB}4. The longer arc — Contrast the day against XIRR only where the history supports it. Skip per-holding divergence-from-thesis for any holding opened within the last ~6 months — its XIRR is annualisation noise. If every holding is that young, say "too early for a long-run read" and move on. Otherwise flag any holding whose XIRR is meaningfully diverging from thesis (sustained underperformance, a winner the portfolio is becoming concentrated in). Never attribute an annualised XIRR to a single recent macro move.
        ${TAB}5. What to watch — One or two forward-looking items from the data: an earnings date, a macro print, a rate decision with its current odds, a pending catalyst. No predictions, just the calendar.

        Style rules:
        $TAB• Front-load every section. The point comes first, the supporting detail after.
        $TAB• Numbers in context. "Up 0.8%, against the S&P's 1.2%" beats "up 0.8%." For a bond book, "up 0.2% with the 10-year 4bp lower" is the equivalent.
        $TAB• No hedging filler ("it's worth noting that…", "interestingly…"). Cut it.
        $TAB• No recommendations to buy or sell. You're a columnist, not an advisor.
        $TAB• Don't compute or show the weighting math. Weights and per-holding contributions are already calculated for you — cite the figures, never derive them (no "(0.118 × 0.20) + …" arithmetic). Explain what moved and why, not how the contribution number was reached.
        $TAB• If data is missing or news is thin, say so plainly rather than padding.
        $TAB• Length: roughly 250–400 words. A morning read, not a research note.
        """.trimIndent()
}