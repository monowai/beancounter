package com.beancounter.common

import com.beancounter.common.model.SystemUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TestSystemUser {

    @Test
    fun is_SystemUserContractHonoured() {
        val sua = SystemUser("abc")
        val sub = SystemUser("abc")
        val suMap = HashMap<SystemUser, SystemUser>()
        suMap[sua] = sua
        suMap[sub] = sub
        assertThat(suMap).hasSize(1).containsKeys(sua, sub)
        assertThat(sua).isEqualTo(sub)
    }
}
