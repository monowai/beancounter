package com.beancounter.marketdata.milestone

import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.UserExplorerAction
import org.springframework.data.repository.CrudRepository

/**
 * CRUD repo for UserExplorerAction.
 */
interface UserExplorerActionRepository : CrudRepository<UserExplorerAction, String> {
    fun findByOwner(owner: SystemUser): List<UserExplorerAction>
}