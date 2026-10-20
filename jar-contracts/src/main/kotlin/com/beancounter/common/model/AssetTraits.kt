package com.beancounter.common.model

/**
 * What a client may do with, and how it shows, an asset. The one source of these rules:
 * clients read the flags off [Asset] and search hits rather than derive them.
 *
 * A category is an [AssetCategory] id or, for a search hit, the type name the provider
 * reports ("Real Estate", "Bank Account"), so both spellings are listed. Matching ignores case.
 */
object AssetTraits {
    const val PRIVATE_MARKET: String = "PRIVATE"
    const val BANK_ACCOUNT: String = "BANK ACCOUNT"
    const val REAL_ESTATE: String = "REAL ESTATE"

    // A private code is `<owner>.<CODE>`, the owner a 22-character id. Public dotted
    // tickers (BRK.B) have short prefixes, so length tells the two apart.
    private const val OWNER_PREFIX_MIN_LENGTH = 12

    private val notChartable =
        setOf(
            AssetCategory.CASH,
            AssetCategory.RE,
            REAL_ESTATE,
            AssetCategory.ACCOUNT,
            BANK_ACCOUNT,
            AssetCategory.TRADE,
            AssetCategory.POLICY,
            // Stored before POLICY replaced it; still a policy, never priced.
            "PENSION"
        )

    private val cashLike = setOf(AssetCategory.CASH, AssetCategory.ACCOUNT, BANK_ACCOUNT)

    /** Has a market price worth charting. */
    fun chartable(category: String): Boolean = category.uppercase() !in notChartable

    /** Cash or a bank account: its code is only a currency or an account handle. */
    fun cashLike(category: String): Boolean = category.uppercase() in cashLike

    /** News (and an AI review built on it) can be found: an asset with a market price, not on the private market. */
    fun hasNews(
        category: String,
        market: String?
    ): Boolean = chartable(category) && !isPrivate(market)

    /** Can go on a watchlist: a market price to follow, so chartable and not private. */
    fun watchable(
        category: String,
        market: String?
    ): Boolean = chartable(category) && !isPrivate(market)

    /** The code a user reads: a private owner prefix dropped, a public dotted ticker left whole. */
    fun displayCode(code: String): String {
        // First dot: a private asset's own code may carry more (owner.BRK.B).
        val dot = code.indexOf('.')
        return if (dot < OWNER_PREFIX_MIN_LENGTH) code else code.substring(dot + 1)
    }

    /**
     * The code of an asset already known to be private, as its owner reads it: everything
     * after the owner id, which never carries a dot. Unlike [displayCode], no length guess.
     */
    fun ownedCode(code: String): String = code.substringAfter('.')

    private fun isPrivate(market: String?): Boolean = PRIVATE_MARKET.equals(market, ignoreCase = true)
}