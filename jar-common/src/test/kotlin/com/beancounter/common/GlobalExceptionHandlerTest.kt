package com.beancounter.common

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.GlobalExceptionHandler
import com.beancounter.common.exception.SystemException
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Assert SpringException details as returned by the MVC GlobalExceptionHandler.
 */
class GlobalExceptionHandlerTest {
    private val request: HttpServletRequest = mock(HttpServletRequest::class.java)
    private val uri = "/mocked/error"
    private val geh = GlobalExceptionHandler()

    @BeforeEach
    fun mockDefaults() {
        `when`(request.requestURI).thenReturn(uri)
    }

    private val message = "detail"

    private val path = "path"

    @Test
    fun is_BadRequest() {
        assertThat(geh.handleBadRequest())
            .isNotNull
            .hasFieldOrPropertyWithValue(
                message,
                "We are unable to process your request."
            )
    }

    @Test
    fun is_SystemException() {
        val se = SystemException("SE")
        assertThat(
            geh.handleSystemException(
                se
            )
        ).isNotNull
            .hasFieldOrPropertyWithValue(
                message,
                se.message
            )
    }

    @Test
    fun is_BusinessException() {
        val be = BusinessException("BE")
        assertThat(
            geh.handleBusinessException(
                be
            )
        ).isNotNull
            .hasFieldOrPropertyWithValue(
                message,
                be.message
            )
    }

    @Test
    fun is_DataIntegrityException() {
        assertThat(
            geh.handleIntegrity(
                BusinessException("DE")
            )
        ).isNotNull
            .hasFieldOrPropertyWithValue(
                message,
                "DE"
            )
    }
}