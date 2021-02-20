package com.beancounter.marketdata.registration

import com.beancounter.common.model.SystemUser
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface SystemUserRepository : CrudRepository<SystemUser?, String?> {
    fun findById(id: String): Optional<SystemUser>
}
