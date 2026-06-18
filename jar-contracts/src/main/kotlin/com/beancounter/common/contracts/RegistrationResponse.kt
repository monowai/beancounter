package com.beancounter.common.contracts

import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.UserPreferences

/**
 * Response to a registration request is a SystemUser with their preferences.
 */
data class RegistrationResponse(
    override val data: SystemUser,
    val preferences: UserPreferences? = null
) : Payload<SystemUser>