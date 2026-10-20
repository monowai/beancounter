package com.beancounter.agent.tools

import com.beancounter.agent.client.WatchlistClient
import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.MarketService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.StaticService
import com.beancounter.common.contracts.CurrencyResponse
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Tools for static reference data (markets, currencies), FX rates, and
 * single-asset spot price and momentum lookups for AI grounding.
 *
 * Beancounter stores every US listing under market `US`; the exchange names a model
 * reaches for (NASDAQ, NYSE, ...) are inactive markets that hold no assets, so a lookup
 * on one that finds nothing is retried once on `US`.
 */
@Service
class MarketTools(
    private val marketService: MarketService,
    private val staticService: StaticService,
    private val fxService: FxService,
    private val tokenService: TokenService,
    private val priceService: PriceService,
    private val watchlistClient: WatchlistClient,
    private val dateUtils: DateUtils = DateUtils()
) {
    @Tool(description = MARKETS_DESC)
    fun listMarkets(): MarketResponse = marketService.getMarkets()

    @Tool(description = "List every supported ISO currency.")
    fun listCurrencies(): CurrencyResponse = staticService.currencies

    @Tool(description = FX_DESC)
    fun getFxRate(
        @ToolParam(description = "Source ISO currency code, e.g. USD") fromCurrency: String,
        @ToolParam(description = "Target ISO currency code, e.g. NZD") toCurrency: String,
        @ToolParam(description = "Rate date YYYY-MM-DD or 'today'") rateDate: String = "today"
    ): FxResponse {
        val request =
            FxRequest(
                rateDate = rateDate,
                pairs = mutableSetOf(IsoCurrencyPair(fromCurrency, toCurrency))
            )
        return fxService.getRates(request, tokenService.bearerToken)
    }

    @Tool(description = PRICE_DESC)
    fun getCurrentPrice(
        @ToolParam(description = MARKET_CODE_DESC) market: String,
        @ToolParam(description = ASSET_CODE_DESC) code: String
    ): CurrentPrice {
        val md = spot(market, code)
        return CurrentPrice(
            assetCode = md.asset.code,
            market = md.asset.market.code,
            priceClose = md.close,
            previousClose = md.previousClose,
            changePercent = md.changePercent,
            priceDate = md.priceDate.toString()
        )
    }

    @Tool(description = MOMENTUM_DESC)
    fun getPriceMomentum(
        @ToolParam(description = MARKET_CODE_DESC) market: String,
        @ToolParam(description = ASSET_CODE_DESC) code: String
    ): PriceMomentum {
        val asset = spot(market, code).asset
        val closes = watchlistClient.getCloses(asset.id, Momentum.historyFrom(dateUtils.date).toString()).toList()
        val momentum = Momentum.of(closes)
        // The close Momentum measures from: same date, so the two can never disagree.
        val latest = momentum.asOf?.let { asOf -> closes.first { it.priceDate.toString() == asOf } }
        return PriceMomentum(
            assetCode = asset.code,
            market = asset.market.code,
            latestClose = latest?.close?.toDouble(),
            latestCloseDate = momentum.asOf,
            momentum = momentum
        )
    }

    /** Today's price for [market]:[code], retrying a US exchange code on BC's `US` market. */
    private fun spot(
        market: String,
        code: String
    ): MarketData {
        val requested = market.trim().uppercase()
        return priceOn(requested, code)
            ?: US_MARKET.takeIf { requested in US_EXCHANGE_CODES }?.let { priceOn(it, code) }
            ?: throw IllegalStateException("No price data for $market:$code")
    }

    private fun priceOn(
        market: String,
        code: String
    ): MarketData? =
        priceService
            .getPrices(
                PriceRequest(
                    date = "today",
                    assets = listOf(PriceAsset(market = market, code = code)),
                    currentMode = true
                ),
                tokenService.bearerToken
            ).data
            .firstOrNull()

    @Tool(description = BENCHMARK_DESC)
    fun getBenchmark(
        @ToolParam(description = BENCHMARK_SCOPE_DESC) scope: String
    ): Map<String, Any> {
        val key = scope.trim().lowercase()
        val proxies = if (key.isBlank() || key in MARKET_ALIASES) INDEX_PROXIES else SECTOR_PROXIES[key]
        if (proxies == null) {
            return mapOf(
                "status" to "unknown_scope",
                "scope" to scope,
                "message" to BENCHMARK_UNKNOWN_SCOPE_MESSAGE,
                "supportedScopes" to (listOf("market") + SECTOR_PROXIES.keys.sorted())
            )
        }
        val response =
            priceService.getPrices(
                PriceRequest(
                    date = "today",
                    assets = proxies.map { PriceAsset(market = it.market, code = it.code) },
                    currentMode = true
                ),
                tokenService.bearerToken
            )
        val byCode = response.data.associateBy { it.asset.code.uppercase() }
        val benchmarks =
            proxies.mapNotNull { proxy ->
                byCode[proxy.code.uppercase()]?.let { md ->
                    mapOf(
                        "name" to proxy.label,
                        "market" to md.asset.market.code,
                        "code" to md.asset.code,
                        "priceClose" to md.close,
                        "previousClose" to md.previousClose,
                        "changePercent" to md.changePercent,
                        "priceDate" to md.priceDate.toString()
                    )
                }
            }
        return if (benchmarks.isEmpty()) {
            mapOf("status" to "no_coverage", "scope" to scope, "message" to BENCHMARK_NO_COVERAGE_MESSAGE)
        } else {
            mapOf("benchmarks" to benchmarks, "count" to benchmarks.size)
        }
    }

    /** Market/sector proxy whose price change stands in for that segment's performance. */
    private data class BenchmarkProxy(
        val market: String,
        val code: String,
        val label: String
    )

    companion object {
        private const val US_MARKET = "US"

        // Inputs svc-data folds onto its US market: the inactive exchange markets, their
        // Sharesight aliases, and the colloquial aliases on US itself.
        private val US_EXCHANGE_CODES =
            setOf("NASDAQ", "NYSE", "AMEX", "ARCA", "NAS", "NYS", "NASAQ", "DOW", "DOW JONES")

        const val MARKET_CODE_DESC =
            "Beancounter market code, e.g. US, LON, ASX, NZX, SGX, or FX for a currency pair. " +
                "US listings use US, whatever exchange they trade on."
        const val ASSET_CODE_DESC = "Asset code as listed on that market, e.g. AAPL"
        const val MARKETS_DESC =
            "List every market Beancounter knows about (US, LON, ASX, NZX, etc.) " +
                "with its trading currency and timezone. US listings use market US."
        const val FX_DESC =
            "Get the foreign exchange rate between two ISO currency codes on a given date. " +
                "Returns the rate, the rate date and the provider."
        const val PRICE_DESC =
            "Get the current public market price for a single asset (close, prior close, " +
                "intraday change %, price date). Use this to ground any forward-looking " +
                "claim — for example, when a news article cites an analyst price target, " +
                "call this to retrieve the current close so you can frame the implied " +
                "growth percentage relative to today's price. Never quote a price target " +
                "without also stating the current close. For trend, drawdown or the 52-week " +
                "range, call getPriceMomentum instead."
        const val MOMENTUM_DESC =
            "Get one asset's price trend from its stored daily closes: latestClose and its date, " +
                "the 52-week high and low closes (high52w, low52w), drawdown from the 52-week high " +
                "(fromHigh52w) and distance above the low (fromLow52w), return1m/3m/6m/12m, and " +
                "vsSma50/vsSma200 (latest close against its 50- and 200-day averages). The return, " +
                "drawdown and average readings are fractions (-0.15 = 15% below); high52w and low52w " +
                "are prices. Each is null only where history is too short. Use it " +
                "for 'down from recent highs', 'price pressure', drawdown, trend or performance " +
                "over a month to a year. Never say 52-week highs or drawdowns are unavailable " +
                "without calling this first."
        const val BENCHMARK_DESC =
            "Get current market or sector benchmark performance (today's price change of a broad " +
                "index or a sector proxy). Use this to contextualise a portfolio's or holding's " +
                "move against the market — e.g. when the user asks how their portfolio is doing " +
                "'versus the market', whether a drop is stock-specific or market-wide, or how a " +
                "sector is performing. Pair the benchmark's changePercent with a holding's " +
                "changePercent to say whether it out- or under-performed. Returns name, close, " +
                "previousClose and changePercent (decimal; -0.026 = -2.6%) per benchmark. NEVER " +
                "mention the underlying data provider."
        const val BENCHMARK_SCOPE_DESC =
            "What to benchmark: 'market' for the broad indices (S&P 500, Nasdaq, Dow), or a sector " +
                "name. Supported sectors: technology, financials, energy, healthcare, " +
                "consumer_discretionary, consumer_staples, industrials, materials, utilities, " +
                "real_estate, communication. One scope per call."
        const val BENCHMARK_UNKNOWN_SCOPE_MESSAGE =
            "Unrecognised scope. Use 'market' for the broad indices or one of the listed sector names."
        const val BENCHMARK_NO_COVERAGE_MESSAGE =
            "No benchmark price available right now for this scope."

        // Broad-market index proxies (priced on the synthetic INDEX market → EODHD `.INDX`).
        private val INDEX_PROXIES =
            listOf(
                BenchmarkProxy("INDEX", "GSPC", "S&P 500"),
                BenchmarkProxy("INDEX", "IXIC", "Nasdaq Composite"),
                BenchmarkProxy("INDEX", "DJI", "Dow Jones Industrial Average")
            )

        // Sector → SPDR sector ETF proxy (priced on the US market). Keys are the scope vocabulary
        // in BENCHMARK_SCOPE_DESC; the ETF's daily move stands in for sector-wide performance.
        private val SECTOR_PROXIES =
            mapOf(
                "technology" to listOf(BenchmarkProxy("US", "XLK", "Technology (XLK)")),
                "financials" to listOf(BenchmarkProxy("US", "XLF", "Financials (XLF)")),
                "energy" to listOf(BenchmarkProxy("US", "XLE", "Energy (XLE)")),
                "healthcare" to listOf(BenchmarkProxy("US", "XLV", "Health Care (XLV)")),
                "consumer_discretionary" to listOf(BenchmarkProxy("US", "XLY", "Consumer Discretionary (XLY)")),
                "consumer_staples" to listOf(BenchmarkProxy("US", "XLP", "Consumer Staples (XLP)")),
                "industrials" to listOf(BenchmarkProxy("US", "XLI", "Industrials (XLI)")),
                "materials" to listOf(BenchmarkProxy("US", "XLB", "Materials (XLB)")),
                "utilities" to listOf(BenchmarkProxy("US", "XLU", "Utilities (XLU)")),
                "real_estate" to listOf(BenchmarkProxy("US", "XLRE", "Real Estate (XLRE)")),
                "communication" to listOf(BenchmarkProxy("US", "XLC", "Communication Services (XLC)"))
            )

        private val MARKET_ALIASES = setOf("market", "markets", "macro", "broad", "overall", "index")
    }
}

/**
 * One asset's [Momentum] with the latest stored close it is measured from; [latestClose]
 * is null when the asset has no price history.
 */
data class PriceMomentum(
    val assetCode: String,
    val market: String,
    val latestClose: Double?,
    val latestCloseDate: String?,
    val momentum: Momentum
)

/**
 * Compact projection of a single asset's spot price returned to the LLM.
 * Mirrors the columnar fields the position tools already expose so the model
 * sees a consistent vocabulary across tool outputs.
 */
data class CurrentPrice(
    val assetCode: String,
    val market: String,
    val priceClose: BigDecimal,
    val previousClose: BigDecimal,
    val changePercent: BigDecimal,
    val priceDate: String
)