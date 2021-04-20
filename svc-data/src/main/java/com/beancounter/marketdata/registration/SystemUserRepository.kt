package com.beancounter.marketdata.registration

import com.beancounter.common.model.SystemUser
import org.springframework.data.repository.CrudRepository
import java.util.Optional

/**
 * CRUD repo for SystemUser.  A SystemUser can own portfolios in BC.
 */
interface SystemUserRepository : CrudRepository<SystemUser?, String?> {
    fun findById(id: String): Optional<SystemUser>
}
