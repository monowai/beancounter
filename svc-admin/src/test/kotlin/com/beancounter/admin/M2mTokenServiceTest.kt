package com.beancounter.admin

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Token exchange against a local stand-in for the Auth0 token endpoint:
 * a fresh token is reused, one inside the refresh skew is re-fetched, and
 * an unconfigured or failing exchange yields null.
 */
internal class M2mTokenServiceTest {
    private lateinit var server: HttpServer
    private val calls = AtomicInteger()

    @Volatile
    private var expiresIn = 3600L

    @Volatile
    private var status = 200

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/oauth/token") { exchange ->
            val n = calls.incrementAndGet()
            val body = """{"access_token":"token-$n","token_type":"Bearer","expires_in":$expiresIn}"""
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    private fun service(issuer: String = "http://127.0.0.1:${server.address.port}/") =
        M2mTokenService(issuer, "https://holdsworth.app", "client", "secret")

    @Test
    fun `should reuse a cached token until it nears expiry`() {
        val service = service()

        assertThat(service.token()).isEqualTo("token-1")
        assertThat(service.token()).isEqualTo("token-1")
        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun `should fetch again when the cached token is inside the refresh skew`() {
        expiresIn = 10L
        val service = service()

        assertThat(service.token()).isEqualTo("token-1")
        assertThat(service.token()).isEqualTo("token-2")
        assertThat(calls.get()).isEqualTo(2)
    }

    @Test
    fun `should return null without calling out when unconfigured`() {
        val service = service(issuer = "")

        assertThat(service.token()).isNull()
        assertThat(calls.get()).isZero()
    }

    @Test
    fun `should return null and retry next time when the exchange fails`() {
        status = 500
        val service = service()

        assertThat(service.token()).isNull()
        status = 200
        assertThat(service.token()).isEqualTo("token-2")
        assertThat(calls.get()).isEqualTo(2)
    }
}