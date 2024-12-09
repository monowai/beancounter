package com.beancounter.common

import com.beancounter.common.model.SystemUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * POJO java contracts
 */
class SystemUserTest {
    @Test
    fun systemUserContractHonoured() {
        val sua = SystemUser("abc")
        val sub = SystemUser("abc")
        val suMap =
            mapOf(
                Pair(
                    sua,
                    sua
                ),
                Pair(
                    sub,
                    sub
                )
            )
        assertThat(suMap).hasSize(1).containsKeys(
            sua,
            sub
        )
        assertThat(sua).isEqualTo(sub)
    }
}