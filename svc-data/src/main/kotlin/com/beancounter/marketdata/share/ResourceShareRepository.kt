package com.beancounter.marketdata.share

import com.beancounter.common.model.ResourceShare
import com.beancounter.common.model.ShareResourceType
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import org.springframework.data.domain.Sort
import org.springframework.data.repository.CrudRepository
import java.util.Optional

/**
 * CRUD for resource sharing relationships (plans, models).
 */
interface ResourceShareRepository : CrudRepository<ResourceShare, String> {
    fun findBySharedWithAndStatusIn(
        sharedWith: SystemUser,
        statuses: Collection<ShareStatus>,
        sort: Sort = Sort.by(Sort.Order.desc("createdAt"))
    ): Iterable<ResourceShare>

    fun findBySharedWithAndResourceTypeAndStatus(
        sharedWith: SystemUser,
        resourceType: ShareResourceType,
        status: ShareStatus
    ): Iterable<ResourceShare>

    fun findByResourceTypeAndResourceIdAndSharedWith(
        resourceType: ShareResourceType,
        resourceId: String,
        sharedWith: SystemUser
    ): Optional<ResourceShare>

    fun findByResourceTypeAndResourceIdAndStatusNot(
        resourceType: ShareResourceType,
        resourceId: String,
        status: ShareStatus
    ): Iterable<ResourceShare>

    fun findByTargetUserAndStatus(
        targetUser: SystemUser,
        status: ShareStatus
    ): Iterable<ResourceShare>

    fun findByResourceOwnerAndResourceTypeAndStatusNot(
        owner: SystemUser,
        resourceType: ShareResourceType,
        status: ShareStatus
    ): Iterable<ResourceShare>
}