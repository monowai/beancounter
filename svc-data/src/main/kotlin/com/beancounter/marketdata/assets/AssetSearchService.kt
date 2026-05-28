package com.beancounter.marketdata.assets

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.assets.figi.FigiConfig
import com.beancounter.marketdata.assets.figi.FigiFilterRequest
import com.beancounter.marketdata.assets.figi.FigiGateway
import com.beancounter.marketdata.assets.figi.FigiSearch
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaProxy
import com.beancounter.marketdata.providers.marketstack.MarketStackConfig
import com.beancounter.marketdata.providers.marketstack.MarketStackGateway
import com.beancounter.marketdata.registration.SystemUserService
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Service for searching assets by keyword.
 * Supports searching private/custom assets and public assets via AlphaVantage or MarketStack.
 */
@Service
class AssetSearchService(
    private val assetRepository: AssetRepository,
    private val alphaProxy: AlphaProxy,
    private val alphaConfig: AlphaConfig,
    private val marketStackGateway: MarketStackGateway,
    private val marketStackConfig: MarketStackConfig,
    private val figiGateway: FigiGateway,
    private val figiConfig: FigiConfig,
    private val marketService: MarketService,
    private val systemUserService: SystemUserService,
    private val mdFactory: MdFactory
) {
    private val log = LoggerFactory.getLogger(AssetSearchService::class.java)

    @Value($$"${beancounter.market.providers.alpha.key:demo}")
    private val alphaApiKey: String = "demo"

    // Markets that use MarketStack for search (configured via mstack.markets)
    private val marketStackMarkets: Set<String> by lazy {
        marketStackConfig.markets
            ?.split(",")
            ?.map { it.trim().uppercase() }
            ?.toSet() ?: emptySet()
    }

    /**
     * Search for assets by keyword.
     * @param keyword The search term (asset code, name, or partial match)
     * @param market Optional market code.
     *               - "PRIVATE": searches user's custom assets only
     *               - "FIGI": searches FIGI globally (expand/force external search)
     *               - null/blank: searches local database only (code + name matching)
     *               - Other values: searches local database (filtered by market) + external provider, merged
     */
    fun search(
        keyword: String,
        market: String?
    ): AssetSearchResponse {
        if (keyword.isBlank() || keyword.length < 2) {
            return AssetSearchResponse(emptyList())
        }

        return when {
            market.equals("PRIVATE", ignoreCase = true) -> searchPrivateAssets(keyword)
            market.equals("FIGI", ignoreCase = true) -> searchFigiGlobal(keyword)
            market.isNullOrBlank() -> searchLocalAssets(keyword)
            else -> searchMarketAssets(keyword, market)
        }
    }

    /**
     * Search for assets in a specific market.
     * Combines local database results (code + name matching) with external provider results,
     * deduplicating by symbol.
     */
    private fun searchMarketAssets(
        keyword: String,
        market: String
    ): AssetSearchResponse {
        // Use the DB-only lookup here. Market-scoped searches go through
        // `searchPublicAssets(market)` for external routing — the broader null-market fan-out
        // inside `searchLocalAssets` would fire scoped + unscoped external calls on the same
        // request, doubling latency and rate-limit pressure for no extra coverage.
        val filtered =
            searchLocalDbAssets(keyword).filter {
                it.market.equals(market, ignoreCase = true)
            }

        val externalResults = searchPublicAssets(keyword, market)
        val localSymbols = filtered.map { it.symbol.uppercase() }.toSet()
        val newFromExternal =
            externalResults.data.filter {
                !localSymbols.contains(it.symbol.uppercase())
            }

        return AssetSearchResponse(filtered + newFromExternal)
    }

    /**
     * Ask the market's configured price provider to search for matching assets. Mirrors price
     * routing — whichever provider [MdFactory] hands back for `market` is the same one that will
     * be priced from, so the search surface and the price surface agree. Returns empty if the
     * market is unknown, the provider has no search wired (default interface impl), or the
     * provider call exceeds [SEARCH_TIMEOUT_MS].
     *
     * The wall-clock cap exists because EODHD's RestClient is sized for batch price downloads
     * (5s connect / 30s read); an interactive header keystroke can't wait that long for one
     * stuck provider while the rest of the chain is alive.
     */
    private fun searchViaConfiguredProvider(
        keyword: String,
        market: String
    ): List<AssetSearchResult> =
        runBlocking {
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                runCatching {
                    val resolved = marketService.getMarket(market)
                    mdFactory.getMarketDataProvider(resolved).searchAssets(keyword, market)
                }.onFailure {
                    log.debug("Provider search for '{}' on {} failed: {}", keyword, market, it.message)
                }.getOrDefault(emptyList())
            } ?: emptyList()
        }

    private fun searchPrivateAssets(keyword: String): AssetSearchResponse {
        val user = systemUserService.getActiveUser()
        if (user == null) {
            log.warn("No active user found for private asset search")
            return AssetSearchResponse(emptyList())
        }

        val assets = assetRepository.searchByUserAndCodeOrName(user.id, keyword)
        val results = assets.map { toSearchResult(it) }
        return AssetSearchResponse(results)
    }

    /**
     * Null-market (header bar) entry point: DB lookup first; on miss, fan out across every
     * registered price provider's search surface plus FIGI global, merge + dedupe by
     * `(symbol, market)`.
     *
     * Market-scoped searches must call [searchLocalDbAssets] directly — the fan-out here is
     * intentionally null-market only.
     */
    private fun searchLocalAssets(keyword: String): AssetSearchResponse {
        val results = searchLocalDbAssets(keyword)
        if (results.isNotEmpty()) return AssetSearchResponse(results)

        // Fan out concurrently — one slow provider (e.g. EODHD with a 5s connect timeout) must
        // not block FIGI/Alpha behind it. supervisorScope isolates failures so a thrown
        // provider doesn't cancel its siblings; withTimeoutOrNull caps the wall-clock wait per
        // provider at SEARCH_TIMEOUT_MS so the user response can't drift beyond that even when
        // the underlying RestClient is configured for longer batch reads.
        val merged =
            runBlocking {
                supervisorScope {
                    val providerTasks =
                        mdFactory.getAllProviders().map { provider ->
                            async(Dispatchers.IO) {
                                withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                                    runCatching {
                                        provider.searchAssets(keyword, null)
                                    }.onFailure {
                                        log.debug(
                                            "Provider {} search for '{}' failed: {}",
                                            provider.getId(),
                                            keyword,
                                            it.message
                                        )
                                    }.getOrDefault(emptyList())
                                } ?: emptyList()
                            }
                        }
                    val figiTask: Deferred<List<AssetSearchResult>>? =
                        if (figiConfig.enabled) {
                            async(Dispatchers.IO) {
                                withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                                    runCatching {
                                        searchFigiGlobal(keyword).data.toList()
                                    }.getOrDefault(emptyList())
                                } ?: emptyList()
                            }
                        } else {
                            null
                        }
                    val providerHits = providerTasks.awaitAll().flatten()
                    val figiHits = figiTask?.await() ?: emptyList()
                    (providerHits + figiHits)
                        .distinctBy {
                            "${it.symbol.uppercase()}|${(it.market ?: "").uppercase()}"
                        }
                        // US-first ordering — header users overwhelmingly look up US tickers,
                        // so surface those before LON/ASX/etc duplicates. Stable sort keeps
                        // intra-group ordering from the concurrent fan-out.
                        .sortedBy {
                            if ((it.market ?: "").uppercase() in US_MARKETS) 0 else 1
                        }
                }
            }
        return AssetSearchResponse(merged)
    }

    /**
     * Pure DB-only lookup with the US-preferred dedupe. Used by both the null-market entry
     * (`searchLocalAssets`) and market-scoped lookups (`searchMarketAssets`) so neither has to
     * pay for the other's external fan-out.
     */
    private fun searchLocalDbAssets(keyword: String): List<AssetSearchResult> {
        val assets = assetRepository.searchByCodeOrName(keyword)
        val marketPriority = listOf("US", "NYSE", "NASDAQ", "ASX", "NZX")
        val deduped =
            assets
                .groupBy { it.code.uppercase() }
                .map { (_, variants) ->
                    variants.minByOrNull { asset ->
                        val idx = marketPriority.indexOf(asset.marketCode)
                        if (idx >= 0) idx else marketPriority.size
                    } ?: variants.first()
                }
        return deduped.take(50).map { toLocalSearchResult(it) }
    }

    // Markets that use FIGI for search (configured via figi.search.markets)
    private val figiSearchMarkets: Set<String> by lazy {
        figiConfig.getSearchMarkets()
    }

    private fun searchPublicAssets(
        keyword: String,
        market: String?
    ): AssetSearchResponse {
        // First ask the market's configured price provider. Symmetric with price routing —
        // EODHD (on kauri) and any other provider that wires `searchAssets` answers here before
        // the FIGI/MarketStack/Alpha chain runs. Non-search providers (cash, custom, morningstar)
        // return empty via the interface default and fall through unchanged.
        if (market != null) {
            val providerHits = searchViaConfiguredProvider(keyword, market)
            if (providerHits.isNotEmpty()) {
                return AssetSearchResponse(providerHits)
            }
        }

        // Use FIGI for markets configured in figi.search.markets.
        // FIGI's filter endpoint returns exact-ticker hits and full-name
        // matches but does poorly on short ticker prefixes (e.g. "COW")
        // because it surfaces option chains that we filter out. Fall back
        // to AlphaVantage SYMBOL_SEARCH when FIGI yields nothing so users
        // get fuzzy ticker/name matches as they type.
        if (market != null && figiSearchMarkets.contains(market.uppercase())) {
            val figiResults = searchFigiAssets(keyword, market)
            if (figiResults.data.isNotEmpty()) return figiResults
            return searchAlphaVantageAssets(keyword, market)
        }

        // Use MarketStack for markets configured in mstack.markets (but not FIGI)
        if (market != null && marketStackMarkets.contains(market.uppercase())) {
            return searchMarketStackAssets(keyword, market)
        }

        // Default to AlphaVantage for other markets
        return searchAlphaVantageAssets(keyword, market)
    }

    private fun searchFigiAssets(
        keyword: String,
        market: String
    ): AssetSearchResponse {
        return try {
            val resolvedMarket = marketService.getMarket(market)
            val exchCode =
                FigiConfig.getExchCode(market.uppercase())
                    ?: error("No FIGI exchange code for market $market")

            // First try exact ticker lookup via /v3/mapping
            val tickerResults = searchFigiByTicker(keyword, exchCode, resolvedMarket, market)
            if (tickerResults.isNotEmpty()) {
                log.debug("FIGI ticker lookup for '{}' found {} results", keyword, tickerResults.size)
                return AssetSearchResponse(tickerResults)
            }

            // Fall back to filter search for name-based matching
            val response =
                figiGateway.filter(
                    FigiFilterRequest(
                        query = keyword,
                        exchCode = exchCode
                    ),
                    figiConfig.apiKey
                )

            log.debug(
                "FIGI filter search for '{}' on {} returned {} results",
                keyword,
                market,
                response.data.size
            )

            val results =
                response.data
                    .filter { result ->
                        val type = result.securityType2?.uppercase()
                        val allowed = type != null && ALLOWED_SECURITY_TYPES.contains(type)
                        if (!allowed && result.ticker != null) {
                            log.debug(
                                "Filtered out {} ({}) - securityType2: {}",
                                result.ticker,
                                result.name,
                                result.securityType2
                            )
                        }
                        allowed
                    }.map { result ->
                        AssetSearchResult(
                            symbol = result.ticker ?: keyword,
                            name = result.name ?: "",
                            type = result.securityType2 ?: "Equity",
                            region = market,
                            currency = resolvedMarket.currency.code,
                            market = market
                        )
                    }

            log.debug("FIGI filter search for '{}' returning {} filtered results", keyword, results.size)
            AssetSearchResponse(results)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.error("Error searching FIGI for assets: ${e.message}", e)
            // Fall back to MarketStack if available
            if (marketStackMarkets.contains(market.uppercase())) {
                return searchMarketStackAssets(keyword, market)
            }
            AssetSearchResponse(emptyList())
        }
    }

    /**
     * Search FIGI globally without exchange restriction.
     * Maps FIGI exchange codes back to BC market codes.
     */
    private fun searchFigiGlobal(keyword: String): AssetSearchResponse =
        try {
            val response =
                figiGateway.filter(
                    FigiFilterRequest(query = keyword),
                    figiConfig.apiKey
                )

            val results =
                response.data
                    .filter { result ->
                        val type = result.securityType2?.uppercase()
                        type != null && ALLOWED_SECURITY_TYPES.contains(type)
                    }.mapNotNull { result ->
                        // Drop results whose FIGI exchange code does not map to a BC
                        // market — the UI would otherwise post the raw FIGI code to
                        // /api/assets and trigger a 404 ("Unable to resolve market
                        // code"). Matches the searchFigiByTicker path which also
                        // requires a configured market.
                        result.exchCode
                            ?.let(FigiConfig::getMarketCode)
                            ?.let { market ->
                                AssetSearchResult(
                                    symbol = result.ticker ?: keyword,
                                    name = result.name ?: "",
                                    type = result.securityType2 ?: "Equity",
                                    region = market,
                                    currency = null,
                                    market = market
                                )
                            }
                    }

            AssetSearchResponse(results)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.error("Error searching FIGI globally: ${e.message}", e)
            AssetSearchResponse(emptyList())
        }

    /**
     * Try exact ticker lookup via FIGI /v3/mapping endpoint.
     * Returns empty list if no match found.
     */
    private fun searchFigiByTicker(
        ticker: String,
        exchCode: String,
        resolvedMarket: Market,
        marketCode: String
    ): List<AssetSearchResult> =
        try {
            val searchRequest =
                FigiSearch(
                    idValue = ticker.uppercase(),
                    exchCode = exchCode
                )
            val responses = figiGateway.search(listOf(searchRequest), figiConfig.apiKey)
            val response = responses.firstOrNull()

            if (response?.error != null || response?.data.isNullOrEmpty()) {
                emptyList()
            } else {
                response.data!!
                    .filter { asset ->
                        val type = asset.securityType2.uppercase()
                        ALLOWED_SECURITY_TYPES.contains(type)
                    }.map { asset ->
                        AssetSearchResult(
                            symbol = asset.ticker,
                            name = asset.name,
                            type = asset.securityType2,
                            region = marketCode,
                            currency = resolvedMarket.currency.code,
                            market = marketCode
                        )
                    }
            }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.debug("FIGI ticker lookup failed for '{}': {}", ticker, e.message)
            emptyList()
        }

    companion object {
        // Per-provider wall-clock cap for interactive asset search. External provider
        // RestClients are sized for batch price downloads (EODHD: 5s connect / 30s read);
        // header-bar keystrokes can't tolerate that latency when one provider is stalled.
        private const val SEARCH_TIMEOUT_MS = 3000L

        // Markets that float to the top of header-bar fan-out results.
        private val US_MARKETS = setOf("US", "NYSE", "NASDAQ", "AMEX")

        private val ALLOWED_SECURITY_TYPES =
            setOf(
                "COMMON STOCK",
                "REIT",
                "DEPOSITARY RECEIPT",
                "MUTUAL FUND"
            )
    }

    private fun searchMarketStackAssets(
        keyword: String,
        market: String
    ): AssetSearchResponse {
        return try {
            val resolvedMarket = marketService.getMarket(market)
            val exchangeMic =
                MarketStackConfig.getMicCode(market)
                    ?: error("No MarketStack MIC code for market $market")

            val response =
                marketStackGateway.searchTickers(
                    exchangeMic = exchangeMic,
                    searchTerm = keyword,
                    apiKey = marketStackConfig.apiKey
                )

            if (response.error != null) {
                log.warn("MarketStack search error: ${response.error}")
                return AssetSearchResponse(emptyList())
            }

            val results =
                response.data?.tickers?.map { ticker ->
                    // Strip the exchange suffix only — `substringBeforeLast` keeps any class
                    // indicator inside the symbol (e.g. "BRK.B.US" → "BRK.B", "D05.SI" → "D05").
                    val assetCode = ticker.symbol.substringBeforeLast(".")
                    AssetSearchResult(
                        symbol = assetCode,
                        name = ticker.name,
                        type = "Equity",
                        region = market,
                        currency = resolvedMarket.currency.code,
                        market = market
                    )
                } ?: emptyList()

            AssetSearchResponse(results)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.error("Error searching MarketStack for assets: ${e.message}", e)
            AssetSearchResponse(emptyList())
        }
    }

    private fun searchAlphaVantageAssets(
        keyword: String,
        market: String?
    ): AssetSearchResponse =
        try {
            val searchSymbol =
                if (market != null && !alphaConfig.isNullMarket(market)) {
                    "${alphaConfig.translateSymbol(keyword)}.$market"
                } else {
                    alphaConfig.translateSymbol(keyword)
                }

            val result = alphaProxy.search(searchSymbol, alphaApiKey)
            val response =
                alphaConfig.getObjectMapper().readValue(
                    result,
                    AssetSearchResponse::class.java
                )

            // Add market info to results
            AssetSearchResponse(
                response.data.map { it.copy(market = market ?: it.region) }
            )
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            // Return empty results on any search failure
            log.error("Error searching AlphaVantage for assets: ${e.message}", e)
            AssetSearchResponse(emptyList())
        }

    private fun toSearchResult(asset: Asset): AssetSearchResult {
        // Private asset codes are stored as "{userId}.{CODE}" — extract the code portion only
        // when the asset truly is PRIVATE. Without the gate, dotted public tickers like BRK.B
        // get their prefix amputated to "B" on the way out.
        val displayCode =
            if (asset.marketCode == "PRIVATE") {
                asset.code.substringAfter(".")
            } else {
                asset.code
            }
        return AssetSearchResult(
            symbol = displayCode,
            name = asset.name ?: displayCode,
            type = asset.category,
            region = "PRIVATE",
            currency = asset.accountingType?.currency?.code,
            market = "PRIVATE",
            assetId = asset.id
        )
    }

    private fun toLocalSearchResult(asset: Asset): AssetSearchResult {
        // For private assets, display code without userId prefix
        val displayCode =
            if (asset.marketCode == "PRIVATE") {
                asset.code.substringAfter(".")
            } else {
                asset.code
            }
        // market is @Transient so resolve currency from MarketService
        val currency =
            asset.accountingType?.currency?.code
                ?: try {
                    marketService.getMarket(asset.marketCode).currency.code
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception
                ) {
                    log.warn("Could not resolve currency for market ${asset.marketCode}: ${e.message}")
                    null
                }
        return AssetSearchResult(
            symbol = displayCode,
            name = asset.name ?: displayCode,
            type = asset.category,
            region = asset.marketCode,
            currency = currency,
            market = asset.marketCode,
            assetId = asset.id
        )
    }
}