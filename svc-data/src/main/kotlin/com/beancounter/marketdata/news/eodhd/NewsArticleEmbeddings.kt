package com.beancounter.marketdata.news.eodhd

import com.beancounter.marketdata.news.NewsArticle
import com.beancounter.marketdata.news.NewsArticleRepo
import com.beancounter.marketdata.news.embedding.EmbeddingCodec
import com.beancounter.marketdata.news.embedding.NewsEmbedder
import com.beancounter.marketdata.news.embedding.NewsEmbeddingProperties
import com.beancounter.marketdata.news.embedding.TopicAnchorStore
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Ingest-time article vectors: decides which incoming articles need a (re)computed embedding,
 * batch-embeds them, and writes the vector plus its derived topics onto the stored row. Runs on
 * the calling thread inside the ingest transaction; the embed call is network I/O against
 * `bc-embed` but only produces plain [FloatArray] values — no entity is touched off-thread.
 */
@Component
class NewsArticleEmbeddings(
    private val newsEmbedder: NewsEmbedder,
    private val embeddingProperties: NewsEmbeddingProperties,
    private val topicAnchorStore: TopicAnchorStore,
    private val newsArticleRepo: NewsArticleRepo
) {
    private val log = LoggerFactory.getLogger(NewsArticleEmbeddings::class.java)

    /**
     * Batch-embeds the [fresh] articles (keyed by external id) that actually need a (re)computed
     * vector, skipping any whose stored row already carries an embedding for the current
     * [NewsEmbedder.modelId] with an unchanged title — EODHD republishes the same article on every
     * poll, so without this every refresh would re-embed its whole feed. A no-op entirely when
     * [newsEmbedder] is inactive (the default), so the noop path never touches the repo for this.
     *
     * The rows loaded here for the currency check are carried out in [Prepared.existing] so the
     * upsert does not re-query every `external_id` a second time — 2N lookups per embed-enabled
     * refresh instead of N.
     */
    fun prepare(fresh: List<Pair<String, EodhdNewsArticle>>): Prepared {
        if (!newsEmbedder.active || fresh.isEmpty()) return Prepared()

        val candidates =
            fresh.map { (externalId, incoming) ->
                Candidate(externalId, incoming, newsArticleRepo.findByExternalId(externalId).orElse(null))
            }
        val existing = candidates.mapNotNull { c -> c.existing?.let { c.externalId to it } }.toMap()
        val toEmbed = candidates.filterNot { isEmbeddingCurrent(it.existing, it.incoming.title) }
        return if (toEmbed.isEmpty()) Prepared(existing = existing) else embed(toEmbed, existing)
    }

    /**
     * Writes [embedding] onto [article]. [textChanged] is whether the incoming payload moved the
     * article's title or content on, captured by the caller before it overwrote them.
     */
    fun applyTo(
        article: NewsArticle,
        embedding: ArticleEmbedding?,
        textChanged: Boolean
    ) {
        // Only present when prepare() actually (re)computed a vector this round — a
        // skip-recompute hit (title + model unchanged) leaves the article's existing
        // embedding/embeddingModel/derivedTopics untouched.
        if (embedding != null) {
            article.embedding = EmbeddingCodec.encode(embedding.vector)
            article.embeddingModel = embedding.model
            article.derivedTopics.clear()
            article.derivedTopics.addAll(
                topicAnchorStore.matchingTopics(embedding.vector, embeddingProperties.topicThreshold)
            )
        } else if (textChanged && article.embedding != null) {
            // Text moved on but no fresh vector was produced — the embedder is inactive, or its
            // batch was discarded. The stored vector and derived topics describe the superseded
            // text, so keeping them would dedup and topic-tag this article against a body it no
            // longer has. Clearing costs one refresh cycle of dedup coverage; the next
            // embed-enabled round recomputes (isEmbeddingCurrent sees a null embedding).
            article.embedding = null
            article.embeddingModel = null
            article.derivedTopics.clear()
        }
    }

    private fun embed(
        toEmbed: List<Candidate>,
        existing: Map<String, NewsArticle>
    ): Prepared {
        val texts = toEmbed.map { embeddingText(it.incoming, it.existing?.summary) }
        val vectors = newsEmbedder.embed(texts)
        if (vectors.size != texts.size) {
            // Partial-batch failure (or a provider that doesn't honour request order/size).
            // Discarding the whole round is deliberate — a positional zip here would silently pair
            // the wrong vector with the wrong article, which is worse than skipping embedding
            // until the next refresh.
            log.warn(
                "news_embedding: {} returned {} vector(s) for {} text(s) — skipping this batch",
                newsEmbedder.modelId,
                vectors.size,
                texts.size
            )
            return Prepared(existing = existing)
        }

        return Prepared(
            embeddings =
                toEmbed
                    .zip(vectors) { candidate, vector ->
                        candidate.externalId to ArticleEmbedding(vector, newsEmbedder.modelId)
                    }.toMap(),
            existing = existing
        )
    }

    private fun isEmbeddingCurrent(
        existing: NewsArticle?,
        incomingTitle: String
    ): Boolean =
        existing != null &&
            existing.embedding != null &&
            existing.embeddingModel == newsEmbedder.modelId &&
            existing.title == incomingTitle

    private fun embeddingText(
        incoming: EodhdNewsArticle,
        existingSummary: String?
    ): String {
        val tail = existingSummary?.takeIf { it.isNotBlank() } ?: incoming.content
        return "${incoming.title}. $tail".take(EMBEDDING_TEXT_MAX_CHARS)
    }

    /** Per-refresh ingest scratch: fresh vectors by externalId, plus the rows already loaded for them. */
    class Prepared(
        val embeddings: Map<String, ArticleEmbedding> = emptyMap(),
        val existing: Map<String, NewsArticle> = emptyMap()
    )

    class ArticleEmbedding(
        val vector: FloatArray,
        val model: String
    )

    private data class Candidate(
        val externalId: String,
        val incoming: EodhdNewsArticle,
        val existing: NewsArticle?
    )

    companion object {
        // Cap on the "title. summary-or-content" text sent to the embedder per article — the
        // model truncates internally anyway, and this keeps the request payload small.
        private const val EMBEDDING_TEXT_MAX_CHARS = 512
    }
}