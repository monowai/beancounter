package com.beancounter.marketdata.portfolio

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.PortfolioShare
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import org.springframework.data.domain.Sort
import org.springframework.data.repository.CrudRepository
import java.util.Optional

/**
 * CRUD for portfolio sharing relationships.
 */
interface PortfolioShareRepository : CrudRepository<PortfolioShare, String> {
    fun findBySharedWithAndStatusIn(
        sharedWith: SystemUser,
        statuses: Collection<ShareStatus>,
        sort: Sort = Sort.by(Sort.Order.desc("createdAt"))
    ): Iterable<PortfolioShare>

    fun findBySharedWithAndStatus(
        sharedWith: SystemUser,
        status: ShareStatus,
        sort: Sort = Sort.by(Sort.Order.asc("portfolio.code"))
    ): Iterable<PortfolioShare>

    fun findByPortfolioAndStatusNot(
        portfolio: Portfolio,
        status: ShareStatus
    ): Iterable<PortfolioShare>

    fun findByPortfolioAndSharedWith(
        portfolio: Portfolio,
        sharedWith: SystemUser
    ): Optional<PortfolioShare>

    fun findByTargetUserAndStatus(
        targetUser: SystemUser,
        status: ShareStatus
    ): Iterable<PortfolioShare>
}