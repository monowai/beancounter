package com.beancounter.common

import com.beancounter.common.Constants.Companion.INT_ERROR
import com.beancounter.common.Constants.Companion.MESSAGE
import com.beancounter.common.Constants.Companion.TEST_UR
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.exception.RecordFailurePredicate
import com.beancounter.common.exception.SpringFeignDecoder
import com.beancounter.common.exception.SystemException
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.utils.BcJson.Companion.objectMapper
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
        val response =
            Response
                .builder()
                .reason("Business Logic")
                .status(HttpStatus.BAD_REQUEST.value())
                .request(
                    Request.create(
                        GET,
                        TEST_UR,
                        java.util.HashMap(),
                        empty(),
                        requestTemplate
                    )
                ).build()
        assertThrows(BusinessException::class.java) {
            validBusinessException(
                springFeignDecoder.decode(
                    Constants.TEST,
                    response
                )
            )
        }
    }

    @Test
    fun is_FeignSystemExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val response =
            Response
                .builder()
                .reason(INT_ERROR)
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .request(
                    Request.create(
                        GET,
                        TEST_UR,
                        java.util.HashMap(),
                        empty(),
                        requestTemplate
                    )
                ).build()
        assertThrows(SystemException::class.java) {
            validSystemException(
                springFeignDecoder.decode(
                    Constants.TEST,
                    response
                )
            )
        }
    }

    @Test
    fun is_FeignExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val response =
            Response
                .builder()
                .reason(INT_ERROR)
                .status(HttpStatus.SWITCHING_PROTOCOLS.value())
                .request(
                    Request.create(
                        GET,
                        TEST_UR,
                        java.util.HashMap(),
                        empty(),
                        requestTemplate
                    )
                ).build()
        assertThrows(FeignException::class.java) {
            val e =
                springFeignDecoder.decode(
                    Constants.TEST,
                    response
                )
            assertThat(e.message).contains("101 Integration Error")
            throw e
        }
    }

    @Test
    fun is_AuthExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val reason = "Unauthorized"
        val response =
            Response
                .builder()
                .reason(reason)
                .status(HttpStatus.UNAUTHORIZED.value())
                .request(
                    Request.create(
                        GET,
                        TEST_UR,
                        HashMap(),
                        empty(),
                        requestTemplate
                    )
                ).build()
        assertThrows(UnauthorizedException::class.java) {
            val e =
                springFeignDecoder.decode(
                    Constants.TEST,
                    response
                )
            assertThat(e.message).contains(reason)
            throw e
        }
    }

    @Test
    fun is_ServiceIntegrationErrorDecoded() {
        val springFeignDecoder = SpringFeignDecoder()
        val errorResponse =
            mapOf(
                "error" to "",
                "message" to INT_ERROR,
                "path" to ""
            )
        val response =
            Response
                .builder()
                .reason("Integration Reason")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .request(
                    Request.create(
                        GET,
                        TEST_UR,
                        HashMap(),
                        empty(),
                        RequestTemplate()
                    )
                ).body(
                    objectMapper.writeValueAsString(errorResponse),
                    Charset.defaultCharset()
                ).build()
        assertThrows(SystemException::class.java) {
            validIntegrationException(
                springFeignDecoder.decode(
                    Constants.TEST,
                    response
                )
            )
        }
    }

    @Test
    fun is_ForbiddenExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val reason = "Forbidden"
        val response =
            Response
                .builder()
                .reason(reason)
                .status(FORBIDDEN.value())
                .request(
                    Request.create(
                        GET,
                        TEST_UR,
                        java.util.HashMap(),
                        empty(),
                        requestTemplate
                    )
                ).build()
        assertThrows(ForbiddenException::class.java) {
            val e =
                springFeignDecoder.decode(
                    Constants.TEST,
                    response
                )
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

    private fun validSystemException(e: Exception) {
        assertThat(e)
            .hasFieldOrPropertyWithValue(
                MESSAGE,
                INT_ERROR
            )
        throw e
    }

    @Suppress("UNCHECKED_CAST")
    private fun validIntegrationException(e: Exception) {
        val errorResponse: Map<String, String> =
            objectMapper.readValue(
                e.message,
                Map::class.java
            ) as Map<String, String>
        assertThat(errorResponse)
            .containsEntry("message", INT_ERROR)
        throw e
    }

    private fun validBusinessException(e: Exception) {
        assertThat(e).hasFieldOrPropertyWithValue(
            MESSAGE,
            "Business Logic"
        )
        throw e
    }
}