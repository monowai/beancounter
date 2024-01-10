package com.beancounter.marketdata.trn

import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate
import java.util.Optional

/**
 * CRUD Repo for business transactions.
 */
interface TrnRepository : CrudRepository<Trn, String> {
    @Query(
        "select t from Trn t " +
            "where t.portfolio.id =?1  " +
            "and t.tradeDate <= ?2",
    )
    fun findByPortfolioId(
        portfolioId: String,
        tradeDate: LocalDate,
        sort: Sort,
    ): Collection<Trn>

    fun deleteByPortfolioId(portfolioId: String): Long

    fun findByPortfolioIdAndId(
        portfolioId: String,
        trnId: String,
    ): Optional<Trn>

    @Query(
        "select t from Trn t " +
            "where t.portfolio.id =?1 " +
            "and t.asset.id = ?2 " +
            "and t.trnType in (?3) ",
    )
    fun findByPortfolioIdAndAssetIdAndTrnType(
        portfolioId: String,
        assetId: String,
        trnType: ArrayList<TrnType>,
        sort: Sort,
    ): Collection<Trn>

    @Query(
        "select t from Trn t " +
            "where t.portfolio.id =?1 " +
            "and t.asset.id = ?2 " +
            "and t.tradeDate <= ?3 " +
            "order by t.tradeDate asc ",
    )
    fun findByPortfolioIdAndAssetIdUpTo(
        id: String,
        assetId: String,
        tradeDate: LocalDate,
    ): Collection<Trn>

    @Query(
        "select t from Trn t  " +
            "where t.portfolio.id =?1  " +
            "and t.asset.id =?2 " +
            "and t.trnType = ?3 " +
            "and t.tradeDate >= ?4 " +
            "and t.tradeDate <= ?5 " +
            "order by t.tradeDate asc ",
    )
    fun findExisting(
        portfolio: String,
        asset: String,
        trnType: TrnType,
        tradeDate: LocalDate,
        endDate: LocalDate,
    ): Collection<Trn>
}
