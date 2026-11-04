package com.beancounter.marketdata.news.eodhd

import com.beancounter.common.telemetry.runBlockingTraced
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.news.NewsFetchRepo
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import com.beancounter.marketdata.providers.eodhd.EodhdProxy
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Re-fetches news keys whose [com.beancounter.marketdata.news.NewsFetch] entry is older than
 * `eodhd.news.refresh-after-hours`, then hands each outcome to [EodhdNewsIngest].
 */
@Component
class EodhdNewsRefresh(
    private val eodhdProxy: EodhdProxy,
    private val eodhdConfig: EodhdConfig,
    private val newsProperties: EodhdNewsProperties,
    private val newsFetchRepo: NewsFetchRepo,
    private val ingest: EodhdNewsIngest,
    private val dateUtils: DateUtils = DateUtils()
) {
    private val log = LoggerFactory.getLogger(EodhdNewsRefresh::class.java)

    // Bounds the upstream fan-out so a large holdings list can't fire one EODHD request per ticker
    // simultaneously — that would amplify load and trip the shared ~1200/day quota in a single burst.
    private val fetchGate = Semaphore(MAX_CONCURRENT_FETCHES)

    /**
     * Refresh each stale key from EODHD. The upstream HTTP call is the slow part — one network
     * round-trip per key — so the fetches fan out across [Dispatchers.IO] and run concurrently.
     * A 10-ticker agent query that previously did 10 serial round-trips (~21s observed in Sentry)
     * collapses to a single round-trip's latency.
     *
     * Persistence stays on the calling thread. Callers run this inside a `@Transactional`
     * boundary, and the bound Hibernate session is single-threaded — DB writes must never touch it
     * from a coroutine dispatcher thread. So coroutines do network-only work; the results are
     * persisted serially here. [runBlockingTraced] keeps the OTel/Sentry span attached across the
     * dispatcher switch (see jar-common CoroutineTracing).
     *
     * @param topicTags topic key -> original topic, from [EodhdNewsSymbols.forTopics]
     */
    fun refreshStale(
        keys: List<String>,
        topicTags: Map<String, String>
    ) {
        val cutoff = LocalDateTime.now(dateUtils.zoneId).minusHours(newsProperties.refreshAfterHours)
        val stale = keys.filter { isStale(it, cutoff) }
        if (stale.isEmpty()) return
        val fetched =
            runBlockingTraced {
                stale
                    .map { key ->
                        async(Dispatchers.IO) { fetchGate.withPermit { key to fetchFor(key, topicTags) } }
                    }.awaitAll()
            }
        for ((key, outcome) in fetched) {
            when (outcome) {
                is FetchResult.Success -> ingest.saveArticles(key, outcome.articles)
                FetchResult.Failure -> ingest.saveFailedFetch(key)
            }
        }
    }

    private fun isStale(
        key: String,
        cutoff: LocalDateTime
    ): Boolean {
        val meta = newsFetchRepo.findById(key).orElse(null)
        return meta == null || meta.lastFetchedAt.isBefore(cutoff)
    }

    /** Dispatch by key shape: a synthetic `TOPIC:` key fetches by topic tag, anything else by ticker. */
    private fun fetchFor(
        key: String,
        topicTags: Map<String, String>
    ): FetchResult = if (EodhdNewsSymbols.isTopicKey(key)) fetchTopic(key, topicTags) else fetchSymbol(key)

    private fun fetchSymbol(symbol: String): FetchResult =
        try {
            FetchResult.Success(
                eodhdProxy.getNews(
                    symbol = symbol,
                    limit = newsProperties.providerLimit,
                    from = null,
                    apiKey = eodhdConfig.apiKey
                )
            )
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.debug("EODHD news lookup failed for {}: {}", symbol, e.message)
            FetchResult.Failure
        }

    private fun fetchTopic(
        key: String,
        topicTags: Map<String, String>
    ): FetchResult =
        try {
            val from = dateUtils.date.minusDays(newsProperties.topicWindowDays).toString()
            FetchResult.Success(
                eodhdProxy.getNewsByTopic(
                    topic = EodhdNewsSymbols.topicTagFor(key, topicTags),
                    limit = newsProperties.providerLimit,
                    from = from,
                    apiKey = eodhdConfig.apiKey
                )
            )
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.debug("EODHD topic news lookup failed for {}: {}", key, e.message)
            FetchResult.Failure
        }

    private sealed interface FetchResult {
        data class Success(
            val articles: List<EodhdNewsArticle>
        ) : FetchResult

        data object Failure : FetchResult
    }

    companion object {
        // Caps simultaneous EODHD news round-trips per request. 8 covers a typical portfolio's
        // holdings in one wave while leaving headroom under the shared daily quota.
        private const val MAX_CONCURRENT_FETCHES = 8
    }
}