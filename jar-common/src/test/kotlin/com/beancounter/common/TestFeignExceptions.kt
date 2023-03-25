package com.beancounter.common

import com.beancounter.common.Constants.Companion.detailMessage
import com.beancounter.common.Constants.Companion.integrationErrorMsg
import com.beancounter.common.Constants.Companion.testUri
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.exception.RecordFailurePredicate
import com.beancounter.common.exception.SpringExceptionMessage
import com.beancounter.common.exception.SpringFeignDecoder
import com.beancounter.common.exception.SystemException
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.utils.BcJson
import com.fasterxml.jackson.core.JsonProcessingException
import feign.FeignException
import feign.Request
import feign.Request.Body.empty
import feign.Request.HttpMethod.GET
import feign.RequestTemplate
import feign.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.FORBIDDEN
import java.nio.charset.Charset

/**
 * Feign decoding and exception handling.
 */
class TestFeignExceptions {
    private val requestTemplate: RequestTemplate = RequestTemplate()

    @Test
    fun is_FeignBusinessExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val response = Response.builder()
            .reason("Business Logic")
            .status(HttpStatus.BAD_REQUEST.value())
            .request(
                Request.create(
                    GET,
                    testUri,
                    java.util.HashMap(),
                    empty(),
                    requestTemplate,
                ),
            )
            .build()
        assertThrows(BusinessException::class.java) {
            validBusinessException(
                springFeignDecoder.decode(Constants.test, response),
            )
        }
    }

    @Test
    fun is_FeignSystemExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val response = Response.builder()
            .reason(integrationErrorMsg)
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .request(
                Request.create(
                    GET,
                    testUri,
                    java.util.HashMap(),
                    empty(),
                    requestTemplate,
                ),
            )
            .build()
        assertThrows(SystemException::class.java) {
            validSystemException(
                springFeignDecoder.decode(Constants.test, response),
            )
        }
    }

    @Test
    fun is_FeignExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val response = Response.builder()
            .reason(integrationErrorMsg)
            .status(HttpStatus.SWITCHING_PROTOCOLS.value())
            .request(
                Request.create(
                    GET,
                    testUri,
                    java.util.HashMap(),
                    empty(),
                    requestTemplate,
                ),
            )
            .build()
        assertThrows(FeignException::class.java) {
            val e = springFeignDecoder.decode(Constants.test, response)
            assertThat(e.message).contains("101 Integration Error")
            throw e
        }
    }

    @Test
    fun is_AuthExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val reason = "Unauthorized"
        val response = Response.builder()
            .reason(reason)
            .status(HttpStatus.UNAUTHORIZED.value())
            .request(
                Request.create(
                    GET,
                    testUri,
                    HashMap(),
                    empty(),
                    requestTemplate,
                ),
            )
            .build()
        assertThrows(UnauthorizedException::class.java) {
            val e = springFeignDecoder.decode(Constants.test, response)
            assertThat(e.message).contains(reason)
            throw e
        }
    }

    @Test
    @Throws(JsonProcessingException::class)
    fun is_ServiceIntegrationErrorDecoded() {
        val springFeignDecoder = SpringFeignDecoder()
        val springExceptionMessage = SpringExceptionMessage(
            error = "",
            message = integrationErrorMsg,
            path = "",
        )
        val response = Response.builder()
            .reason("Integration Reason")
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .request(
                Request.create(
                    GET,
                    testUri,
                    HashMap(),
                    empty(),
                    RequestTemplate(),
                ),
            )
            .body(
                BcJson().objectMapper.writeValueAsString(springExceptionMessage),
                Charset.defaultCharset(),
            )
            .build()
        assertThrows(SystemException::class.java) {
            validIntegrationException(
                springFeignDecoder.decode(Constants.test, response),
            )
        }
    }

    @Test
    fun is_ForbiddenExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val reason = "Forbidden"
        val response = Response.builder()
            .reason(reason)
            .status(FORBIDDEN.value())
            .request(
                Request.create(
                    GET,
                    testUri,
                    java.util.HashMap(),
                    empty(),
                    requestTemplate,
                ),
            )
            .build()
        assertThrows(ForbiddenException::class.java) {
            val e = springFeignDecoder.decode(Constants.test, response)
            assertThat(e.message).contains(reason)
            throw e
        }
    }

    @Test
    fun is_PredicateAssumptions() {
        val recordFailurePredicate = RecordFailurePredicate()
        assertThat(recordFailurePredicate.test(BusinessException("User Error"))).isFalse
        assertThat(recordFailurePredicate.test(SystemException("System Error"))).isTrue
    }

    @Throws(Exception::class)
    private fun validSystemException(e: Exception) {
        assertThat(e)
            .hasFieldOrPropertyWithValue(detailMessage, integrationErrorMsg)
        throw e
    }

    @Throws(Exception::class)
    private fun validIntegrationException(e: Exception) {
        val springExceptionMessage: SpringExceptionMessage = BcJson()
            .objectMapper.readValue(e.message, SpringExceptionMessage::class.java)
        assertThat(springExceptionMessage)
            .hasFieldOrPropertyWithValue("message", integrationErrorMsg)
        throw e
    }

    @Throws(Exception::class)
    private fun validBusinessException(e: Exception) {
        assertThat(e).hasFieldOrPropertyWithValue(detailMessage, "Business Logic")
        throw e
    }
}
