package com.beancounter.common

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.KeyGenUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class TestKeyGenUtils {
    @Test
    fun is_uuidGenerating() {
        val uuid = UUID.randomUUID()
        val webSafe = KeyGenUtils.format(uuid)
        assertThat(webSafe).isNotNull
        assertThat(KeyGenUtils.parse(webSafe).compareTo(uuid)).isEqualTo(0)
        assertThat(KeyGenUtils.parse(uuid.toString()).compareTo(uuid)).isEqualTo(0)
    }

    @Test
    fun is_ArgumentExceptionsCorrect() {
        assertThrows(BusinessException::class.java) { KeyGenUtils.format(null) }
        assertThrows(BusinessException::class.java) { KeyGenUtils.parse(null) }
        assertThrows(BusinessException::class.java) { KeyGenUtils.parse("ABC") }
        assertThrows(BusinessException::class.java) { KeyGenUtils.parse("12345678901234567") }
        assertThrows(BusinessException::class.java) { KeyGenUtils.parse("") }
    }
}
