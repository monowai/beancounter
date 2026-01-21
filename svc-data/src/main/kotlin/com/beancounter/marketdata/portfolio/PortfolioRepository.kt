package com.beancounter.marketdata.portfolio

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate
import java.util.Optional

/**
 * Portfolio CRUD interface.
 */
interface PortfolioRepository : CrudRepository<Portfolio, String> {
    fun findByCodeAndOwner(
        code: String,
        systemUser: SystemUser
    ): Optional<Portfolio>

    fun findByOwner(
        systemUser: SystemUser,
        sort: Sort = Sort.by(Sort.Order.asc("code"))
    ): Iterable<Portfolio>

    fun findByOwnerAndActive(
        systemUser: SystemUser,
        active: Boolean,
        sort: Sort = Sort.by(Sort.Order.asc("code"))
    ): Iterable<Portfolio>

    fun findByActive(
        active: Boolean,
        sort: Sort = Sort.by(Sort.Order.asc("code"))
    ): Iterable<Portfolio>

    @Query(
        "select distinct t.portfolio from Trn t " +
            "where (t.asset.id = ?1 and (t.cashAsset.id is null or t.cashAsset.id <> ?1)) " +
            "and t.tradeDate <= ?2 " +
            "and t.portfolio.active = true"
    )
    fun findDistinctPortfolioByAssetIdAndTradeDate(
        assetId: String,
        tradeDate: LocalDate
    ): Collection<Portfolio>
}