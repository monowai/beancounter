package com.beancounter.common.model

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * Represents a portfolio sharing relationship between an owner (client)
 * and another user (adviser). Tracks access level and invitation status.
 *
 * When [status] is PENDING_ADVISER_REQUEST, [portfolio] may be null because
 * the adviser is requesting general access - the client will later choose
 * which portfolios to share.
 */
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_portfolio_share",
            columnNames = ["portfolio_id", "shared_with_id"]
        )
    ]
)
data class PortfolioShare(
    @Id override val id: String = KeyGenUtils().id,
    @ManyToOne val portfolio: Portfolio? = null,
    @ManyToOne override val sharedWith: SystemUser,
    @Enumerated(EnumType.STRING)
    val accessLevel: ShareAccessLevel = ShareAccessLevel.FULL,
    @Enumerated(EnumType.STRING)
    override var status: ShareStatus = ShareStatus.PENDING_CLIENT_INVITE,
    val createdAt: Instant = Instant.now(),
    @ManyToOne override val createdBy: SystemUser,
    @ManyToOne override val targetUser: SystemUser? = null,
    override var acceptedAt: Instant? = null
) : Share {
    override fun owner(): SystemUser? = portfolio?.owner
}