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

    private val message = "message"

    private val path = "path"

    @Test
    fun is_BadRequest() {
        assertThat(geh.handleBadRequest(request))
            .isNotNull
            .hasNoNullFieldsOrProperties()
            .hasFieldOrPropertyWithValue(message, "Message not readable")
            .hasFieldOrPropertyWithValue(path, uri)
    }

    @Test
    fun is_SystemException() {
        val se = SystemException("SE")
        assertThat(geh.handleSystemException(request, se))
            .isNotNull
            .hasNoNullFieldsOrProperties()
            .hasFieldOrPropertyWithValue(message, se.message)
            .hasFieldOrPropertyWithValue(path, uri)
    }

    @Test
    fun is_BusinessException() {
        val be = BusinessException("BE")
        assertThat(geh.handleBusinessException(request, be))
            .isNotNull
            .hasNoNullFieldsOrProperties()
            .hasFieldOrPropertyWithValue(message, be.message)
            .hasFieldOrPropertyWithValue(path, uri)
    }

    @Test
    fun is_DataIntegrityException() {
        assertThat(geh.handleIntegrity(request, BusinessException("DE")))
            .isNotNull
            .hasNoNullFieldsOrProperties()
            .hasFieldOrPropertyWithValue(message, "Data integrity violation")
            .hasFieldOrPropertyWithValue(path, uri)
    }
}
