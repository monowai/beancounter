package com.beancounter.marketdata.assets.search

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.model.Asset
import com.beancounter.common.telemetry.runBlockingTraced
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.custom.PrivateMarketDataProvider
import com.beancounter.marketdata.registration.SystemUserService
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for searching assets by keyword.
 * Supports searching private/custom assets, the local database, and public assets through the
 * market's price provider, falling back to [ProviderSearchChain].
 */
@Service
class AssetSearchService(
    private val assetRepository: AssetRepository,
    private val figiAssetSearch: FigiAssetSearch,
    private val providerSearchChain: ProviderSearchChain,
    private val marketService: MarketService,
    private val systemUserService: SystemUserService,
    private val mdFactory: MdFactory
) {
    private val log = LoggerFactory.getLogger(AssetSearchService::class.java)

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

        val response =
            when {
                market.equals(PrivateMarketDataProvider.ID, ignoreCase = true) -> searchPrivateAssets(keyword)
                market.equals("FIGI", ignoreCase = true) -> figiAssetSearch.global(keyword)
                market.isNullOrBlank() -> searchLocalAssets(keyword)
                else -> searchMarketAssets(keyword, market)
            }
        return AssetSearchResponse(response.data.filter { onSupportedMarket(it) })
    }

    /**
     * Final guard: only surface results that sit on a resolvable Beancounter market. The lookup UI
     * posts a result's `market` to `/api/assets`, which rejects unknown codes with a 404 — so a
     * result the system can't place on a BC market is unusable noise. Per-provider mapping (EODHD,
     * FIGI) already emits BC codes; this is the central net that catches anything slipping past them,
     * including future providers that return their own venue codes.
     */
    private fun onSupportedMarket(result: AssetSearchResult): Boolean {
        val code = result.market
        if (code.isNullOrBlank()) return false
        // An unmappable venue is the normal case this guard exists to filter, so ask a
        // question that has a null answer rather than catching an exception that has
        // already marked the caller's transaction rollback-only (#1088).
        return marketService.getMarketOrNull(code) != null
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
        val filtered = searchLocalDbAssets(keyword, market)

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
     * routing — whichever provider [MdFactory] hands back for `market` is the same one that
     * will be priced from, so the search surface and the price surface agree.
     *
     * Note on the [withTimeoutOrNull] wrapper: it can only cancel suspending code. Blocking
     * Spring `RestClient` calls inside the provider don't poll the cancellation flag, so the
     * real wall-clock cap is enforced at the HTTP layer — see `eodhdSearchRestClient` in
     * [com.beancounter.marketdata.config.ExternalApiRestClientConfig]. The coroutine timeout
     * stays as belt-and-suspenders for any future provider with short suspension points.
     */
    private fun searchViaConfiguredProvider(
        keyword: String,
        market: String
    ): List<AssetSearchResult> =
        runBlockingTraced {
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
        val dbResults = searchLocalDbAssets(keyword)

        // Short-circuit only when the DB has an exact ticker match — otherwise a name-only
        // substring hit (e.g. "TSM" matching "Huntsman") would shadow real ticker results
        // from providers (Taiwan Semi). Substring-only DB hits get merged with the fan-out.
        val hasExactCode = dbResults.any { it.symbol.equals(keyword, ignoreCase = true) }
        if (hasExactCode) return AssetSearchResponse(dbResults)

        // Fan out concurrently — one slow provider must not block FIGI/Alpha behind it.
        // supervisorScope isolates failures so a thrown provider doesn't cancel its siblings.
        // The withTimeoutOrNull wrapper is best-effort — blocking RestClient calls inside the
        // providers don't cooperatively cancel, so the real wall-clock cap comes from the
        // per-client HTTP timeouts (see `eodhdSearchRestClient`).
        val external =
            runBlockingTraced {
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
                        if (figiAssetSearch.enabled) {
                            async(Dispatchers.IO) {
                                withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                                    runCatching {
                                        figiAssetSearch.global(keyword).data.toList()
                                    }.getOrDefault(emptyList())
                                } ?: emptyList()
                            }
                        } else {
                            null
                        }
                    providerTasks.awaitAll().flatten() + (figiTask?.await() ?: emptyList())
                }
            }
        val merged =
            (dbResults + external)
                .distinctBy {
                    "${it.symbol.uppercase()}|${(it.market ?: "").uppercase()}"
                }
                // US-first ordering — header users overwhelmingly look up US tickers,
                // so surface those before LON/ASX/etc duplicates. Stable sort keeps
                // intra-group ordering from the DB-then-fan-out merge.
                .sortedBy {
                    if ((it.market ?: "").uppercase() in US_MARKETS) 0 else 1
                }
        return AssetSearchResponse(merged)
    }

    /**
     * Pure DB-only lookup. Used by both the null-market entry (`searchLocalAssets`) and
     * market-scoped lookups (`searchMarketAssets`) so neither has to pay for the other's
     * external fan-out.
     *
     * When [market] is given, results are scoped to that market only — no cross-market
     * dedupe, since discarding a row here would make a market-scoped search seem to have no
     * local match even when one exists (e.g. same code listed on both US and LON).
     *
     * When [market] is null (header bar), the same code can legitimately appear on more than
     * one market (e.g. SMOT on both US and LON) — every variant is returned, US-first, so the
     * caller's market-grouped display can surface all of them rather than silently dropping
     * whichever market lost an internal tiebreak.
     */
    private fun searchLocalDbAssets(
        keyword: String,
        market: String? = null
    ): List<AssetSearchResult> {
        val assets = assetRepository.searchByCodeOrName(keyword)
        val scoped =
            if (market != null) {
                assets.filter { it.marketCode.equals(market, ignoreCase = true) }
            } else {
                assets.sortedBy { if (it.marketCode.uppercase() in US_MARKETS) 0 else 1 }
            }
        return scoped.take(LOCAL_SEARCH_LIMIT).map { toLocalSearchResult(it) }
    }

    private fun searchPublicAssets(
        keyword: String,
        market: String?
    ): AssetSearchResponse {
        // First ask the market's configured price provider. Symmetric with price routing —
        // EODHD (on kauri) and any other provider that wires `searchAssets` answers here before
        // the FIGI/MarketStack/Alpha chain runs. Non-search providers (cash, custom, morningstar)
        // return empty via the interface default and fall through unchanged.
        val providerHits = if (market != null) searchViaConfiguredProvider(keyword, market) else emptyList()
        return if (providerHits.isNotEmpty()) {
            AssetSearchResponse(providerHits)
        } else {
            providerSearchChain.search(keyword, market)
        }
    }

    companion object {
        private const val LOCAL_SEARCH_LIMIT = 50

        // Per-provider wall-clock cap for interactive asset search. External provider
        // RestClients are sized for batch price downloads (EODHD: 5s connect / 30s read);
        // header-bar keystrokes can't tolerate that latency when one provider is stalled.
        private const val SEARCH_TIMEOUT_MS = 3000L

        // Markets that float to the top of header-bar fan-out results.
        private val US_MARKETS = setOf("US", "NYSE", "NASDAQ", "AMEX")
    }

    private fun toSearchResult(asset: Asset): AssetSearchResult {
        // Private asset codes are stored as "{userId}.{CODE}" — extract the code portion only
        // when the asset truly is PRIVATE. Without the gate, dotted public tickers like BRK.B
        // get their prefix amputated to "B" on the way out.
        val displayCode =
            if (asset.marketCode == PrivateMarketDataProvider.ID) {
                asset.code.substringAfter(".")
            } else {
                asset.code
            }
        return AssetSearchResult(
            symbol = displayCode,
            name = asset.name ?: displayCode,
            type = asset.category,
            region = PrivateMarketDataProvider.ID,
            currency = asset.accountingType?.currency?.code,
            market = PrivateMarketDataProvider.ID,
            assetId = asset.id
        )
    }

    private fun toLocalSearchResult(asset: Asset): AssetSearchResult {
        // For private assets, display code without userId prefix
        val displayCode =
            if (asset.marketCode == PrivateMarketDataProvider.ID) {
                asset.code.substringAfter(".")
            } else {
                asset.code
            }
        // market is @Transient so resolve currency from MarketService
        val currency =
            asset.accountingType?.currency?.code
                ?: marketService.getMarketOrNull(asset.marketCode)?.currency?.code
                ?: run {
                    // Unresolvable market means "no currency to show", not a failure — and
                    // catching getMarket's exception poisoned the transaction (#1088).
                    log.warn("Could not resolve currency for market ${asset.marketCode}")
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