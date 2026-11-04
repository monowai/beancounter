package com.beancounter.marketdata.news.eodhd

import com.beancounter.marketdata.news.NewsArticle
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
    private val topicAnchorStore: TopicAnchorStore
) {
    private val log = LoggerFactory.getLogger(NewsArticleEmbeddings::class.java)

    /**
     * Batch-embeds the [fresh] articles (keyed by external id) that actually need a (re)computed
     * vector, skipping any whose stored row in [existing] already carries an embedding for the
     * current [NewsEmbedder.modelId] and unchanged text (see [textChanged]) — EODHD republishes
     * the same article on every poll, so without this every refresh would re-embed its whole
     * feed. A no-op entirely when [newsEmbedder] is inactive (the default). [existing] is the
     * ingest's one preload of the stored rows, so this issues no queries of its own.
     *
     * @return fresh vectors by external id; an article absent from it keeps its stored vector.
     */
    fun prepare(
        fresh: List<Pair<String, EodhdNewsArticle>>,
        existing: Map<String, NewsArticle>
    ): Map<String, ArticleEmbedding> {
        if (!newsEmbedder.active) return emptyMap()
        val toEmbed =
            fresh
                .map { (externalId, incoming) -> Candidate(externalId, incoming, existing[externalId]) }
                .filterNot { isEmbeddingCurrent(it.existing, it.incoming) }
        return if (toEmbed.isEmpty()) emptyMap() else embed(toEmbed)
    }

    /**
     * Writes [embedding] onto [article]. [textChanged] is [Companion.textChanged] for the incoming
     * payload, captured by the caller before it overwrote the article's title and content.
     */
    fun applyTo(
        article: NewsArticle,
        embedding: ArticleEmbedding?,
        textChanged: Boolean
    ) {
        // Only present when prepare() actually (re)computed a vector this round — a
        // skip-recompute hit (text + model unchanged) leaves the article's existing
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

    private fun embed(toEmbed: List<Candidate>): Map<String, ArticleEmbedding> {
        val texts = toEmbed.map { embeddingText(it.incoming) }
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
            return emptyMap()
        }

        return toEmbed
            .zip(vectors) { candidate, vector ->
                candidate.externalId to ArticleEmbedding(vector, newsEmbedder.modelId)
            }.toMap()
    }

    private fun isEmbeddingCurrent(
        existing: NewsArticle?,
        incoming: EodhdNewsArticle
    ): Boolean =
        existing != null &&
            existing.embedding != null &&
            existing.embeddingModel == newsEmbedder.modelId &&
            !textChanged(existing, incoming)

    /** Built from the same fields [textChanged] compares, so a vector describes what is stored. */
    private fun embeddingText(incoming: EodhdNewsArticle): String =
        "${incoming.title}. ${incoming.content}".take(EMBEDDING_TEXT_MAX_CHARS)

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
        /**
         * The one definition of "this article's text moved on": the title or content differs. It
         * decides both whether a stored vector is still current and whether ingest clears one.
         */
        fun textChanged(
            stored: NewsArticle,
            incoming: EodhdNewsArticle
        ): Boolean = stored.title != incoming.title || stored.content != incoming.content

        // Cap on the "title. content" text sent to the embedder per article — the
        // model truncates internally anyway, and this keeps the request payload small.
        private const val EMBEDDING_TEXT_MAX_CHARS = 512
    }
}