package com.beancounter.auth.model

import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.model.SystemUser

/**
 * Register an authenticated token with the service so that it can
 * create data.
 */
interface Registration {
    fun register(systemUser: SystemUser): RegistrationResponse
}
