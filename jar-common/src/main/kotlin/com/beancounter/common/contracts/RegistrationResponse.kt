package com.beancounter.common.contracts

import com.beancounter.common.model.SystemUser

/**
 * Response to a registration request is a SystemUser.
 */
data class RegistrationResponse(
    override val data: SystemUser
) : Payload<SystemUser>