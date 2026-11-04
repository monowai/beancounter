package com.beancounter.marketdata.news.eodhd

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.news.NewsArticle
import com.beancounter.marketdata.news.NewsArticleRepo
import com.beancounter.marketdata.news.NewsArticleTicker
import com.beancounter.marketdata.news.NewsFetch
import com.beancounter.marketdata.news.NewsFetchRepo
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Persists one EODHD fetch outcome for a news key: upserts the articles into [NewsArticle] and
 * stamps the key's [NewsFetch] cooldown. Runs on the calling thread and joins the caller's
 * transaction — the bound Hibernate session is single-threaded, so this must never be called from
 * a coroutine dispatcher thread.
 */
@Component
class EodhdNewsIngest(
    private val newsArticleRepo: NewsArticleRepo,
    private val newsFetchRepo: NewsFetchRepo,
    private val embeddings: NewsArticleEmbeddings,
    private val dateUtils: DateUtils = DateUtils()
) {
    /** Upserts [articles] fetched for [symbol] and records the successful fetch. */
    fun saveArticles(
        symbol: String,
        articles: List<EodhdNewsArticle>
    ) {
        val keyed = articles.map { externalIdOf(it) to it }
        val existing = preload(keyed.map { it.first })
        val fresh = embeddings.prepare(keyed, existing)
        for ((externalId, incoming) in keyed) {
            saveOrMerge(symbol, incoming, fresh[externalId], existing[externalId])
        }
        newsFetchRepo.save(NewsFetch(symbol, LocalDateTime.now(dateUtils.zoneId), articles.size))
    }

    /**
     * Burn the refresh cooldown even on failure — otherwise a transient EODHD outage or 429 turns
     * every subsequent request into a quota-amplifying retry storm. Next attempt waits
     * `refresh-after-hours`, matching the success-path backoff. Articles already in the DB keep
     * serving in the meantime.
     */
    fun saveFailedFetch(symbol: String) {
        val existing =
            newsFetchRepo
                .findById(symbol)
                .orElseGet { NewsFetch(ticker = symbol, lastFetchedAt = LocalDateTime.now(dateUtils.zoneId)) }
        existing.lastFetchedAt = LocalDateTime.now(dateUtils.zoneId)
        newsFetchRepo.save(existing)
    }

    /**
     * One query for every stored row this fetch touches, shared by the embedding currency check
     * and the upsert. The id list is one EODHD response, capped by `provider-limit` (EODHD itself
     * serves at most 1000), so it stays far below the database's bind-parameter limit unchunked.
     */
    private fun preload(externalIds: List<String>): Map<String, NewsArticle> =
        if (externalIds.isEmpty()) {
            emptyMap()
        } else {
            newsArticleRepo.findAllByExternalIdIn(externalIds).associateBy { it.externalId }
        }

    /** EODHD's link is the dedup key; a blank one falls back to the date+title pair. */
    private fun externalIdOf(incoming: EodhdNewsArticle): String =
        incoming.link.ifBlank { "${incoming.date}|${incoming.title}" }

    /**
     * Defensive upsert: read-then-save can race when two concurrent refreshes for overlapping
     * tickers find the same `external_id` missing and both try to insert. The DB unique constraint
     * catches one transaction with a [DataIntegrityViolationException]; we then re-fetch the row
     * the winner inserted and merge our fields into it. Idempotent — at worst we do one extra read
     * + write on contention.
     */
    private fun saveOrMerge(
        symbol: String,
        incoming: EodhdNewsArticle,
        embedding: NewsArticleEmbeddings.ArticleEmbedding?,
        existing: NewsArticle?
    ) {
        val externalId = externalIdOf(incoming)
        val article =
            existing ?: NewsArticle(
                externalId = externalId,
                // Placeholder — applyIncoming() overwrites both stamps immediately below.
                published = LocalDateTime.now(dateUtils.zoneId),
                fetchedAt = LocalDateTime.now(dateUtils.zoneId)
            )
        applyIncoming(article, symbol, incoming, embedding)
        try {
            newsArticleRepo.save(article)
        } catch (e: DataIntegrityViolationException) {
            val winner = newsArticleRepo.findByExternalId(externalId).orElseThrow { e }
            applyIncoming(winner, symbol, incoming, embedding)
            newsArticleRepo.save(winner)
        }
    }

    private fun applyIncoming(
        article: NewsArticle,
        symbol: String,
        incoming: EodhdNewsArticle,
        embedding: NewsArticleEmbeddings.ArticleEmbedding?
    ) {
        // Captured before the overwrite below — decides whether a vector carried over from a
        // previous round still describes this article.
        val textChanged = article.title != incoming.title || article.content != incoming.content

        article.externalId = externalIdOf(incoming)
        article.published = parsePublished(incoming.date)
        article.title = incoming.title
        article.content = incoming.content
        article.link = incoming.link
        incoming.sentiment?.let { s ->
            article.polarity = s.polarity
            article.sentimentPos = s.pos
            article.sentimentNeg = s.neg
            article.sentimentNeu = s.neu
        }
        article.source = "EODHD"
        article.fetchedAt = LocalDateTime.now(dateUtils.zoneId)

        // Refresh the tag set — EODHD can revise tags between fetches.
        article.tags.clear()
        article.tags.addAll(incoming.tags)

        // Symbols: keep raw EODHD tickers + ensure the queried symbol is always present so a
        // ticker that only appears in `symbols[]` on one update isn't lost.
        val symbolSet = (incoming.symbols + symbol).toSet()
        val existingTickers = article.tickerLinks.map { it.ticker }.toSet()
        for (t in symbolSet - existingTickers) {
            article.tickerLinks.add(NewsArticleTicker(ticker = t))
        }

        embeddings.applyTo(article, embedding, textChanged)
    }

    private fun parsePublished(raw: String): LocalDateTime =
        try {
            // EODHD ships `2026-05-15T05:15:00+00:00` — keep an OffsetDateTime parse for safety.
            OffsetDateTime.parse(raw).toLocalDateTime()
        } catch (
            @Suppress("SwallowedException")
            e: DateTimeParseException
        ) {
            try {
                LocalDateTime.parse(raw)
            } catch (
                @Suppress("SwallowedException")
                e2: DateTimeParseException
            ) {
                LocalDateTime.now(ZoneOffset.UTC)
            }
        }
}