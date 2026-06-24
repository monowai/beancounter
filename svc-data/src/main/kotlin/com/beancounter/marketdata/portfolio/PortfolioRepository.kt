package com.beancounter.marketdata.portfolio

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
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

    /**
     * Bulk idempotent delete of every portfolio owned by [ownerId]. Issued as a
     * single SQL DELETE rather than a select-then-remove loop, so concurrent or
     * repeated offboarding calls (e.g. the bc-view wizard firing /offboard/wealth
     * and /offboard/account in parallel) affect 0 rows instead of raising
     * StaleObjectStateException. Matches the pattern used by
     * TrnRepository.deleteByPortfolioId (Sentry DATA-5P / DATA-5Q).
     */
    @Modifying
    @Query("delete from Portfolio p where p.owner.id = :ownerId")
    fun deleteByOwnerId(
        @Param("ownerId") ownerId: String
    ): Long
}