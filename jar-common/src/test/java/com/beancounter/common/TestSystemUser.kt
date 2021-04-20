package com.beancounter.common

import com.beancounter.common.model.SystemUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * POJO java contracts
 */
class TestSystemUser {

    @Test
    fun systemUserContractHonoured() {
        val sua = SystemUser("abc")
        val sub = SystemUser("abc")
        val suMap = HashMap<SystemUser, SystemUser>()
        suMap[sua] = sua
        suMap[sub] = sub
        assertThat(suMap).hasSize(1).containsKeys(sua, sub)
        assertThat(sua).isEqualTo(sub)
    }
}
