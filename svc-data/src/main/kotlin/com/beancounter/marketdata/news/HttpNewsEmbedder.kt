package com.beancounter.marketdata.news

import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * [NewsEmbedder] backed by a standalone HuggingFace Text Embeddings Inference (TEI) deployment
 * (`bc-embed`, deployed separately from svc-data — see MCP.md). Keeping the model out-of-process
 * sidesteps onnxruntime's glibc-only native library entirely: svc-data's production image is
 * Alpine (musl libc), so an in-JVM ONNX embedder could never run there. Gated behind
 * `beancounter.market.news.embedding.enabled=true` — see [NewsEmbeddingConfig]; [NoopNewsEmbedder]
 * is the only [NewsEmbedder] bean otherwise.
 *
 * TEI contract: `POST {url}/embed` with `{"inputs": ["text", ...]}` — the response is a JSON
 * array of float arrays in request order. Requests are chunked at [NewsEmbeddingProperties.batchSize].
 * Vectors are normalized to unit length here (never trusting server-side config) so callers can
 * treat a dot product as cosine similarity.
 *
 * Failures degrade gracefully — any transport or parse error is logged (with the throwable) and
 * produces an empty result rather than an exception.
 * [com.beancounter.marketdata.news.eodhd.EodhdNewsService]'s ingest path treats "no vectors back"
 * as "skip embedding for this batch, retry next refresh", never as a reason to fail the whole news
 * refresh — a `bc-embed` outage must not take EODHD ingest down with it.
 */
class HttpNewsEmbedder(
    private val restClient: RestClient,
    private val properties: NewsEmbeddingProperties
) : NewsEmbedder {
    private val log = LoggerFactory.getLogger(HttpNewsEmbedder::class.java)

    override val modelId: String = properties.modelId
    override val active: Boolean = true

    override fun embed(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        return texts.chunked(properties.batchSize.coerceAtLeast(1)).flatMap(::embedBatch)
    }

    private fun embedBatch(batch: List<String>): List<FloatArray> =
        try {
            restClient
                .post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(TeiRequest(batch))
                .retrieve()
                .body(VECTORS_TYPE)
                ?.map { vector -> VectorMath.normalize(vector.map(Double::toFloat).toFloatArray()) }
                ?: emptyList()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("news_embedding: /embed call to {} failed for a batch of {}", properties.url, batch.size, e)
            emptyList()
        }

    private data class TeiRequest(
        val inputs: List<String>
    )

    companion object {
        private val VECTORS_TYPE = object : ParameterizedTypeReference<List<List<Double>>>() {}
    }
}