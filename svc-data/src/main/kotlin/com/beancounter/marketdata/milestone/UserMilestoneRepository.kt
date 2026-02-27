package com.beancounter.marketdata.milestone

import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.UserMilestone
import org.springframework.data.repository.CrudRepository
import java.util.Optional

/**
 * CRUD repo for UserMilestone.
 */
interface UserMilestoneRepository : CrudRepository<UserMilestone, String> {
    fun findByOwner(owner: SystemUser): List<UserMilestone>

    fun findByOwnerAndMilestoneId(
        owner: SystemUser,
        milestoneId: String
    ): Optional<UserMilestone>
}