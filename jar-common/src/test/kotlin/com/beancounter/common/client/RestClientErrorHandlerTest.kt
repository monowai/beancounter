package com.beancounter.common.client

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.exception.SystemException
import com.beancounter.common.exception.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mock.http.client.MockClientHttpResponse
import java.net.URI

/**
 * Verifies that 4xx responses log at WARN (client / caller fault) while 5xx
 * responses log at ERROR (server / "get-out-of-bed" issues). Prevents
 * Sentry noise from controllers that probe-and-fallback (e.g. POSITION-40).
 */
class RestClientErrorHandlerTest {
    private lateinit var logger: Logger
    private lateinit var appender: ListAppender<ILoggingEvent>
    private val handler = RestClientErrorHandler()
    private val uri = URI.create("http://bc-data/api/portfolios/DBS")

    @BeforeEach
    fun attachAppender() {
        logger = LoggerFactory.getLogger(RestClientErrorHandler::class.java) as Logger
        appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
    }

    @Test
    fun `404 logs at WARN and throws NotFoundException`() {
        assertThatThrownBy {
            handler.handleError(uri, HttpMethod.GET, response(HttpStatus.NOT_FOUND, "Portfolio not found: DBS"))
        }.isInstanceOf(NotFoundException::class.java)
        assertThat(appender.list).singleElement().satisfies({
            assertThat(it.level).isEqualTo(Level.WARN)
            assertThat(it.formattedMessage).contains("404").contains("Portfolio not found: DBS")
        })
    }

    @Test
    fun `401 logs at WARN and throws UnauthorizedException`() {
        assertThatThrownBy { handler.handleError(uri, HttpMethod.GET, response(HttpStatus.UNAUTHORIZED, "no token")) }
            .isInstanceOf(UnauthorizedException::class.java)
        assertThat(appender.list).singleElement().satisfies({
            assertThat(it.level).isEqualTo(Level.WARN)
        })
    }

    @Test
    fun `403 logs at WARN and throws ForbiddenException`() {
        assertThatThrownBy { handler.handleError(uri, HttpMethod.GET, response(HttpStatus.FORBIDDEN, "nope")) }
            .isInstanceOf(ForbiddenException::class.java)
        assertThat(appender.list).singleElement().satisfies({
            assertThat(it.level).isEqualTo(Level.WARN)
        })
    }

    @Test
    fun `400 logs at WARN and throws BusinessException`() {
        assertThatThrownBy { handler.handleError(uri, HttpMethod.GET, response(HttpStatus.BAD_REQUEST, "bad input")) }
            .isInstanceOf(BusinessException::class.java)
        assertThat(appender.list).singleElement().satisfies({
            assertThat(it.level).isEqualTo(Level.WARN)
        })
    }

    @Test
    fun `500 logs at ERROR and throws SystemException`() {
        assertThatThrownBy {
            handler.handleError(
                uri,
                HttpMethod.GET,
                response(HttpStatus.INTERNAL_SERVER_ERROR, "boom")
            )
        }.isInstanceOf(SystemException::class.java)
        assertThat(appender.list).singleElement().satisfies({
            assertThat(it.level).isEqualTo(Level.ERROR)
            assertThat(it.formattedMessage).contains("500").contains("boom")
        })
    }

    @Test
    fun `503 logs at ERROR and throws SystemException`() {
        assertThatThrownBy {
            handler.handleError(
                uri,
                HttpMethod.GET,
                response(HttpStatus.SERVICE_UNAVAILABLE, "down")
            )
        }.isInstanceOf(SystemException::class.java)
        assertThat(appender.list).singleElement().satisfies({
            assertThat(it.level).isEqualTo(Level.ERROR)
        })
    }

    private fun response(
        status: HttpStatus,
        body: String
    ): ClientHttpResponse = MockClientHttpResponse(body.toByteArray(Charsets.UTF_8), status)
}