package com.beancounter.agent.clients

import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpMethod
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.reactive.ClientHttpConnector
import org.springframework.http.client.reactive.ClientHttpRequestDecorator
import org.springframework.http.client.reactive.JdkClientHttpConnector
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.net.URI

/** Rewrites a serialized DeepSeek chat-completion request body. */
typealias BodyRewrite = (ByteArray, ObjectMapper) -> ByteArray

/**
 * Shapes DeepSeek thinking mode by rewriting the outgoing chat-completion
 * request body, for the two cases Spring AI 2.0.1 cannot express:
 *
 * - [disableThinking] — `"thinking": {"type": "disabled"}` for the "fast"
 *   client. DeepSeek Flash defaults to thinking mode, which adds large latency
 *   and reasoning-token cost to pre-canned prompts.
 * - [lowEffort] — `"reasoning_effort": "low"` for the thinking (Chat FAB)
 *   client. DeepSeek accepts none/low/high/max, but Spring AI's
 *   `ReasoningEffort` enum only has HIGH and MAX (beancounter#1128).
 *
 * Each rewrite goes on a **dedicated** DeepSeek client, applied the same way to
 * the sync (RestClient, [interceptor]) and streaming (WebClient, [connector])
 * transports.
 */
object DeepSeekThinking {
    private val log = LoggerFactory.getLogger(DeepSeekThinking::class.java)

    const val THINKING = "thinking"
    const val TYPE = "type"
    const val DISABLED = "disabled"
    const val REASONING_EFFORT = "reasoning_effort"
    const val LOW = "low"

    /** Return [body] with `thinking: {type: disabled}` added (or overwritten). */
    fun disableThinking(
        body: ByteArray,
        mapper: ObjectMapper
    ): ByteArray =
        rewrite(body, mapper) { root ->
            root.set(THINKING, mapper.createObjectNode().put(TYPE, DISABLED))
        }

    /**
     * Return [body] with `reasoning_effort: low` unless the request already
     * chose an effort — the deep tier sets `high` per call and keeps it.
     */
    fun lowEffort(
        body: ByteArray,
        mapper: ObjectMapper
    ): ByteArray =
        rewrite(body, mapper) { root ->
            if (!root.has(REASONING_EFFORT)) root.put(REASONING_EFFORT, LOW)
        }

    /**
     * Apply [change] to the parsed body. On any parse failure the original body
     * is returned unchanged — never fail a request over request-shaping.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun rewrite(
        body: ByteArray,
        mapper: ObjectMapper,
        change: (ObjectNode) -> Unit
    ): ByteArray =
        try {
            val root = mapper.readTree(body)
            if (root is ObjectNode) {
                change(root)
                mapper.writeValueAsBytes(root)
            } else {
                body
            }
        } catch (e: Exception) {
            log.warn("Could not rewrite DeepSeek request body, sending original: {}", e.message)
            body
        }

    /** RestClient interceptor for the sync `/chat/completions` call. */
    fun interceptor(
        mapper: ObjectMapper,
        bodyRewrite: BodyRewrite
    ): ClientHttpRequestInterceptor =
        ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            val mutated = bodyRewrite(body, mapper)
            if (mutated !== body) {
                request.headers.contentLength = mutated.size.toLong()
            }
            execution.execute(request, mutated)
        }

    /**
     * Reactive connector for the streaming `/chat/completions` call (WebClient).
     * Buffers the outgoing request body, applies [bodyRewrite], and re-emits
     * it. Delegates to a stock [JdkClientHttpConnector] (no reactor-netty needed).
     */
    fun connector(
        mapper: ObjectMapper,
        bodyRewrite: BodyRewrite
    ): ClientHttpConnector =
        ClientHttpConnector { method: HttpMethod, uri: URI, requestCallback ->
            JdkClientHttpConnector().connect(method, uri) { request ->
                requestCallback.apply(
                    object : ClientHttpRequestDecorator(request) {
                        override fun writeWith(body: Publisher<out DataBuffer>): Mono<Void> =
                            DataBufferUtils.join(body).flatMap { joined ->
                                val bytes = ByteArray(joined.readableByteCount())
                                joined.read(bytes)
                                DataBufferUtils.release(joined)
                                val mutated = bodyRewrite(bytes, mapper)
                                headers.contentLength = mutated.size.toLong()
                                super.writeWith(Mono.just(bufferFactory().wrap(mutated)))
                            }
                    }
                )
            }
        }
}