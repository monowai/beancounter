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
class KeyGenUtilsTest {
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
    fun staticUuids() {
        // 9b14c934-854e-4701-9e30-6e4a03343068 // mxTJNIVORwGeMG5KAzQwaA
        // 9b14c934-854e-4701-9e30-6e4a03343000
        verify(
            UUID(
                -7271966270884526335,
                -7048012152674439064
            )
        )
        verify(
            UUID(
                46696621207273647,
                -5135048203407689936
            )
        )
        verify(
            UUID(
                -6965057034889835965,
                -6067888309988012771
            )
        )
        verify(
            UUID(
                1674956389701140531,
                -7919210516680745918
            )
        )
    }

    private fun verify(uuid: UUID) {
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