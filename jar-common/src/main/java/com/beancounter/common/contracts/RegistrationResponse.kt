package com.beancounter.common.contracts

import com.beancounter.common.model.SystemUser

data class RegistrationResponse(override val data: SystemUser) : Payload<SystemUser>
