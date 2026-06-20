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

/**
 * Forces DeepSeek **non-thinking** mode by injecting `"thinking": {"type":
 * "disabled"}` into the outgoing chat-completion request body.
 *
 * DeepSeek v4-flash defaults to thinking mode, which adds large latency and
 * reasoning-token cost. Spring AI 2.0's `DeepSeekChatOptions` / request record
 * carry no field for it, so we mutate the serialized JSON body at the HTTP
 * layer. The mutation is applied to a **dedicated** "fast" DeepSeek client only;
 * the default (thinking) client is untouched.
 *
 * Body mutation is the same for the sync (RestClient) and streaming (WebClient)
 * transports — see [interceptor] and the WebClient connector that both call
 * [disableThinking].
 */
object DeepSeekNonThinking {
    private val log = LoggerFactory.getLogger(DeepSeekNonThinking::class.java)

    const val THINKING = "thinking"
    const val TYPE = "type"
    const val DISABLED = "disabled"

    /**
     * Return [body] with `thinking: {type: disabled}` added (or overwritten).
     * On any parse failure the original body is returned unchanged — never fail
     * a request over telemetry-shaping.
     */
    @Suppress("TooGenericExceptionCaught")
    fun disableThinking(
        body: ByteArray,
        mapper: ObjectMapper
    ): ByteArray =
        try {
            val root = mapper.readTree(body)
            if (root is ObjectNode) {
                root.set(THINKING, mapper.createObjectNode().put(TYPE, DISABLED))
                mapper.writeValueAsBytes(root)
            } else {
                body
            }
        } catch (e: Exception) {
            log.warn("Could not inject non-thinking flag, sending original body: {}", e.message)
            body
        }

    /** RestClient interceptor for the sync `/chat/completions` call. */
    fun interceptor(mapper: ObjectMapper): ClientHttpRequestInterceptor =
        ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            val mutated = disableThinking(body, mapper)
            if (mutated !== body) {
                request.headers.contentLength = mutated.size.toLong()
            }
            execution.execute(request, mutated)
        }

    /**
     * Reactive connector for the streaming `/chat/completions` call (WebClient).
     * Buffers the outgoing request body, applies [disableThinking], and re-emits
     * it. Delegates to a stock [JdkClientHttpConnector] (no reactor-netty needed).
     */
    fun connector(mapper: ObjectMapper): ClientHttpConnector =
        ClientHttpConnector { method: HttpMethod, uri: URI, requestCallback ->
            JdkClientHttpConnector().connect(method, uri) { request ->
                requestCallback.apply(
                    object : ClientHttpRequestDecorator(request) {
                        override fun writeWith(body: Publisher<out DataBuffer>): Mono<Void> =
                            DataBufferUtils.join(body).flatMap { joined ->
                                val bytes = ByteArray(joined.readableByteCount())
                                joined.read(bytes)
                                DataBufferUtils.release(joined)
                                val mutated = disableThinking(bytes, mapper)
                                headers.contentLength = mutated.size.toLong()
                                super.writeWith(Mono.just(bufferFactory().wrap(mutated)))
                            }
                    }
                )
            }
        }
}