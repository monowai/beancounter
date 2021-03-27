package com.beancounter.common

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.ForbiddenException
import com.beancounter.common.exception.RecordFailurePredicate
import com.beancounter.common.exception.SpringExceptionMessage
import com.beancounter.common.exception.SpringFeignDecoder
import com.beancounter.common.exception.SystemException
import com.beancounter.common.exception.UnauthorizedException
import com.beancounter.common.utils.BcJson
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import feign.FeignException
import feign.Request
import feign.RequestTemplate
import feign.Response
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.nio.charset.Charset
import java.util.Date
import java.util.HashMap

internal class TestExceptions {
    private val requestTemplate = RequestTemplate()
    @Test
    fun is_FeignBusinessExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val response = Response.builder()
            .reason("Business Logic")
            .status(HttpStatus.BAD_REQUEST.value())
            .request(
                Request.create(
                    Request.HttpMethod.GET, "/test",
                    HashMap(), Request.Body.empty(), requestTemplate
                )
            )
            .build()
        Assertions.assertThrows(BusinessException::class.java) {
            validBusinessException(
                springFeignDecoder.decode("test", response)
            )
        }
    }

    @Throws(Exception::class)
    private fun validBusinessException(e: Exception) {
        org.assertj.core.api.Assertions.assertThat(e).hasFieldOrPropertyWithValue(
            "detailMessage",
            "Business Logic"
        )
        throw e
    }

    @Test
    fun is_FeignSystemExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val response = Response.builder()
            .reason("Integration Error")
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .request(
                Request.create(
                    Request.HttpMethod.GET, "/test", HashMap(),
                    Request.Body.empty(), requestTemplate
                )
            )
            .build()
        Assertions.assertThrows(SystemException::class.java) {
            validSystemException(
                springFeignDecoder.decode("test", response)
            )
        }
    }

    @Test
    fun is_FeignExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val response = Response.builder()
            .reason("Integration Error")
            .status(HttpStatus.SWITCHING_PROTOCOLS.value())
            .request(
                Request.create(
                    Request.HttpMethod.GET, "/test", HashMap(),
                    Request.Body.empty(), requestTemplate
                )
            )
            .build()
        Assertions.assertThrows(FeignException::class.java) {
            val e = springFeignDecoder.decode("test", response)
            org.assertj.core.api.Assertions.assertThat(e.message).contains("101 Integration Error")
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
                    Request.HttpMethod.GET, "/test", HashMap(),
                    Request.Body.empty(), requestTemplate
                )
            )
            .build()
        Assertions.assertThrows(UnauthorizedException::class.java) {
            val e = springFeignDecoder.decode("test", response)
            org.assertj.core.api.Assertions.assertThat(e.message).contains(reason)
            throw e
        }
    }

    @Test
    fun is_ForbiddenExceptionThrown() {
        val springFeignDecoder = SpringFeignDecoder()
        val reason = "Forbidden"
        val response = Response.builder()
            .reason(reason)
            .status(HttpStatus.FORBIDDEN.value())
            .request(
                Request.create(
                    Request.HttpMethod.GET, "/test", HashMap(),
                    Request.Body.empty(), requestTemplate
                )
            )
            .build()
        Assertions.assertThrows(ForbiddenException::class.java) {
            val e = springFeignDecoder.decode("test", response)
            org.assertj.core.api.Assertions.assertThat(e.message).contains(reason)
            throw e
        }
    }

    @Throws(Exception::class)
    private fun validSystemException(e: Exception) {
        org.assertj.core.api.Assertions.assertThat(e).hasFieldOrPropertyWithValue("detailMessage", "Integration Error")
        throw e
    }

    @Test
    @Throws(JsonProcessingException::class)
    fun is_ServiceIntegrationErrorDecoded() {
        val springFeignDecoder = SpringFeignDecoder()
        val springExceptionMessage = SpringExceptionMessage(
            Date(),
            500,
            "",
            "Integration Error",
            ""
        )
        val response = Response.builder()
            .reason("Integration Reason")
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .request(
                Request.create(
                    Request.HttpMethod.GET, "/test", HashMap(),
                    Request.Body.empty(), requestTemplate
                )
            )
            .body(
                BcJson().objectMapper.writeValueAsString(springExceptionMessage),
                Charset.defaultCharset()
            )
            .build()
        Assertions.assertThrows(SystemException::class.java) {
            validIntegrationException(
                springFeignDecoder.decode("test", response)
            )
        }
    }

    @Throws(Exception::class)
    private fun validIntegrationException(e: Exception) {
        org.assertj.core.api.Assertions.assertThat(e)
            .hasFieldOrPropertyWithValue("detailMessage", "Integration Error")
        throw e
    }

    @Test
    fun is_ExceptionsBodiesCorrect() {
        Assertions.assertThrows(BusinessException::class.java) { throwBusinessException() }
        Assertions.assertThrows(SystemException::class.java) { throwSystemException() }
    }

    @Test
    fun is_PredicateAssumptions() {
        val recordFailurePredicate = RecordFailurePredicate()
        org.assertj.core.api.Assertions.assertThat(recordFailurePredicate.test(BusinessException("User Error"))).isFalse
        org.assertj.core.api.Assertions.assertThat(recordFailurePredicate.test(SystemException("System Error"))).isTrue
    }

    @Test
    @Throws(Exception::class)
    fun is_SpringErrorSerializable() {
        val springExceptionMessage = SpringExceptionMessage(
            Date(),
            418,
            "I'm a teapot",
            "Message",
            "/test"
        )
        val mapper = ObjectMapper().registerModule(KotlinModule())
        val json = mapper.writeValueAsString(springExceptionMessage)
        val fromJson = mapper.readValue(json, SpringExceptionMessage::class.java)
        org.assertj.core.api.Assertions.assertThat(fromJson).usingRecursiveComparison()
            .isEqualTo(springExceptionMessage)
    }

    private fun throwBusinessException() {
        val businessException = BusinessException("Test Message")
        org.assertj.core.api.Assertions.assertThat(businessException)
            .hasFieldOrPropertyWithValue("detailMessage", "Test Message")
        throw businessException
    }

    private fun throwSystemException() {
        val systemException = SystemException("Test Message")
        org.assertj.core.api.Assertions.assertThat(systemException)
            .hasFieldOrPropertyWithValue("detailMessage", "Test Message")
        throw systemException
    }
}
