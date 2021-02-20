package com.beancounter.marketdata.utils

import com.beancounter.common.model.SystemUser
import java.util.UUID

object SysUserUtils {
    @JvmStatic
    val systemUser: SystemUser
        get() = SystemUser(UUID.randomUUID().toString())
}
