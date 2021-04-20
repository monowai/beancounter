package com.beancounter.common

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.KeyGenUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Coverage of KeyGenUtils
 */
class TestKeyGenUtils {
    private var keyGenUtils = KeyGenUtils()
    @Test
    fun uuidGenerating() {
        val uuid = UUID.randomUUID()
        val webSafe = keyGenUtils.format(uuid)
        assertThat(webSafe).isNotNull
        assertThat(keyGenUtils.parse(webSafe).compareTo(uuid)).isEqualTo(0)
        assertThat(keyGenUtils.parse(uuid.toString()).compareTo(uuid)).isEqualTo(0)
    }

    @Test
    fun argumentParsingExceptions() {
        assertThrows(BusinessException::class.java) { keyGenUtils.format(null) }
        assertThrows(BusinessException::class.java) { keyGenUtils.parse(null) }
        assertThrows(BusinessException::class.java) { keyGenUtils.parse("ABC") }
        assertThrows(BusinessException::class.java) { keyGenUtils.parse("12345678901234567") }
        assertThrows(BusinessException::class.java) { keyGenUtils.parse("") }
    }
}
