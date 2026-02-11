package com.beancounter.common.model

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * Sharing relationship for non-portfolio resources (independence plans, rebalance models).
 * The resource itself lives in another service; only [resourceId] is stored here.
 */
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_resource_share",
            columnNames = ["resource_type", "resource_id", "shared_with_id"]
        )
    ]
)
data class ResourceShare(
    @Id val id: String = KeyGenUtils().id,
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    val resourceType: ShareResourceType,
    @Column(name = "resource_id", nullable = false)
    val resourceId: String,
    val resourceName: String? = null,
    @ManyToOne val sharedWith: SystemUser,
    @Enumerated(EnumType.STRING)
    val accessLevel: ShareAccessLevel = ShareAccessLevel.VIEW,
    @Enumerated(EnumType.STRING)
    var status: ShareStatus = ShareStatus.PENDING_CLIENT_INVITE,
    val createdAt: Instant = Instant.now(),
    @ManyToOne val createdBy: SystemUser,
    @ManyToOne val resourceOwner: SystemUser,
    @ManyToOne val targetUser: SystemUser? = null,
    var acceptedAt: Instant? = null
)