package com.beancounter.common

import com.beancounter.common.Constants.Companion.MESSAGE
import com.beancounter.common.Constants.Companion.TEST_UR
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.SpringExceptionMessage
import com.beancounter.common.exception.SystemException
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Coverage of basic BC Exception and Message functionality.
 */
internal class ExceptionMessagesTest {
    private val testMessage = "Test Message"

    @Test
    fun is_ExceptionsBodiesCorrect() {
        assertThrows(BusinessException::class.java) { throwBusinessException() }
        assertThrows(SystemException::class.java) { throwSystemException() }
    }

    @Test
    fun is_SpringErrorSerializable() {
        val springExceptionMessage =
            SpringExceptionMessage(
                error = "I'm a teapot",
                message = "Message",
                path = TEST_UR,
            )
        val json = objectMapper.writeValueAsString(springExceptionMessage)
        val fromJson = objectMapper.readValue(json, SpringExceptionMessage::class.java)
        assertThat(fromJson)
            .hasNoNullFieldsOrProperties()
            .usingRecursiveComparison()
            .isEqualTo(springExceptionMessage)
    }

    private fun throwBusinessException() {
        val businessException = BusinessException(testMessage)
        assertThat(businessException)
            .hasFieldOrPropertyWithValue(MESSAGE, testMessage)
        throw businessException
    }

    private fun throwSystemException() {
        val systemException = SystemException(testMessage)
        assertThat(systemException)
            .hasFieldOrPropertyWithValue(MESSAGE, testMessage)
        throw systemException
    }
}
