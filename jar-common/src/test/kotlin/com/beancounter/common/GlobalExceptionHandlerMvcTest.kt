package com.beancounter.common

import com.beancounter.common.exception.GlobalExceptionHandler
import io.sentry.Sentry
import io.sentry.SentryEvent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Drives GlobalExceptionHandler through the real MVC resolver chain: an unmapped exception
 * must come back as a 500 and reach Sentry, while exceptions Spring already maps keep their status.
 */
class GlobalExceptionHandlerMvcTest {
    private val captured = CopyOnWriteArrayList<SentryEvent>()

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(ThrowingController())
            .setControllerAdvice(GlobalExceptionHandler(sentryEnabled = true))
            .build()

    @BeforeEach
    fun initSentry() {
        Sentry.init { options ->
            options.dsn = "https://key@localhost/1"
            options.setBeforeSend { event, _ ->
                captured.add(event)
                null
            }
        }
    }

    @AfterEach
    fun closeSentry() {
        Sentry.close()
    }

    @Test
    fun `should return 500 and capture to Sentry when an unmapped exception escapes a controller`() {
        val result = mockMvc.perform(get("/unmapped")).andReturn()

        assertThat(result.response.status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value())
        assertThat(result.response.contentAsString).contains("Type com.example.Missing not present")
        assertThat(captured.map { it.throwable })
            .singleElement()
            .isInstanceOf(TypeNotPresentException::class.java)
    }

    @Test
    fun `should keep the status of a ResponseStatus annotated exception`() {
        val result = mockMvc.perform(get("/annotated")).andReturn()

        assertThat(result.response.status).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value())
        assertThat(captured).isEmpty()
    }

    @Test
    fun `should keep the status of a ResponseStatusException`() {
        val result = mockMvc.perform(get("/status")).andReturn()

        assertThat(result.response.status).isEqualTo(HttpStatus.I_AM_A_TEAPOT.value())
        assertThat(captured).isEmpty()
    }

    @Test
    fun `should keep the 400 Spring MVC returns for a missing request parameter`() {
        val result = mockMvc.perform(get("/param")).andReturn()

        assertThat(result.response.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
        assertThat(captured).isEmpty()
    }

    @Test
    fun `should leave Spring Security exceptions to the security filter chain`() {
        assertThatThrownBy { mockMvc.perform(get("/denied")) }
            .hasRootCauseInstanceOf(AccessDeniedException::class.java)
        assertThat(captured).isEmpty()
    }

    @Test
    fun `should return 400 for an IllegalArgumentException`() {
        val result = mockMvc.perform(get("/illegal")).andReturn()

        assertThat(result.response.status).isEqualTo(HttpStatus.BAD_REQUEST.value())
        assertThat(result.response.contentAsString).contains("bad argument")
        assertThat(captured).isEmpty()
    }

    @Test
    fun `should not report a client disconnect to Sentry`() {
        val result = mockMvc.perform(get("/disconnected")).andReturn()

        assertThat(result.response.contentAsString).isEmpty()
        assertThat(captured).isEmpty()
    }

    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    class AnnotatedException : RuntimeException("slow down")

    @RestController
    class ThrowingController {
        @GetMapping("/unmapped")
        fun unmapped(): String = throw TypeNotPresentException("com.example.Missing", null)

        @GetMapping("/annotated")
        fun annotated(): String = throw AnnotatedException()

        @GetMapping("/status")
        fun status(): String = throw ResponseStatusException(HttpStatus.I_AM_A_TEAPOT)

        @GetMapping("/param")
        fun param(
            @RequestParam required: String
        ): String = required

        @GetMapping("/illegal")
        fun illegal(): String = throw IllegalArgumentException("bad argument")

        @GetMapping("/disconnected")
        fun disconnected(): String = throw IOException("Broken pipe")

        @GetMapping("/denied")
        fun denied(): String = throw AccessDeniedException("no")
    }
}