package com.beancounter.common

import com.beancounter.common.Constants.Companion.detailMessage
import com.beancounter.common.Constants.Companion.testUri
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.SpringExceptionMessage
import com.beancounter.common.exception.SystemException
import com.beancounter.common.utils.BcJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Coverage of basic BC Exception and Message functionality.
 */
internal class TestExceptionMessages {

    private val testMessage = "Test Message"

    @Test
    fun is_ExceptionsBodiesCorrect() {
        assertThrows(BusinessException::class.java) { throwBusinessException() }
        assertThrows(SystemException::class.java) { throwSystemException() }
    }

    @Test
    @Throws(Exception::class)
    fun is_SpringErrorSerializable() {
        val springExceptionMessage = SpringExceptionMessage(
            status = 418,
            error = "I'm a teapot",
            message = "Message",
            path = testUri
        )
        val mapper = BcJson().objectMapper
        val json = mapper.writeValueAsString(springExceptionMessage)
        val fromJson = mapper.readValue(json, SpringExceptionMessage::class.java)
        assertThat(fromJson)
            .hasNoNullFieldsOrProperties()
            .usingRecursiveComparison()
            .isEqualTo(springExceptionMessage)
    }

    private fun throwBusinessException() {
        val businessException = BusinessException(testMessage)
        assertThat(businessException)
            .hasFieldOrPropertyWithValue(detailMessage, testMessage)
        throw businessException
    }

    private fun throwSystemException() {
        val systemException = SystemException(testMessage)
        assertThat(systemException)
            .hasFieldOrPropertyWithValue(detailMessage, testMessage)
        throw systemException
    }
}
