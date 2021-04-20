package com.beancounter.marketdata.portfolio

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate
import java.util.Optional

/**
 * Portfolio CRUD interface.
 */
interface PortfolioRepository : CrudRepository<Portfolio, String> {
    fun findByCodeAndOwner(code: String, systemUser: SystemUser): Optional<Portfolio>
    fun findByOwner(systemUser: SystemUser): Iterable<Portfolio>

    @Query("select distinct t.portfolio from Trn t where t.asset.id = ?1 and t.tradeDate <= ?2")
    fun findDistinctPortfolioByAssetIdAndTradeDate(
        assetId: String,
        tradeDate: LocalDate
    ): Collection<Portfolio>
}
