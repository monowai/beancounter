package com.beancounter.marketdata.fx

import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate

interface FxRateRepository : CrudRepository<FxRate, String> {
    @Query("select f from FxRate f where ((f.date >= :date and f.date <= :date) or f.date <= :earlyDate)")
    fun findByDateRange(
        date: LocalDate,
        earlyDate: LocalDate = LocalDate.of(1900, 1, 1),
    ): List<FxRate>

    @Query("select f from FxRate f where f.from = :from and f.to = :from and f.date <= :earlyDate")
    fun findBaseRate(
        from: Currency,
        earlyDate: LocalDate = LocalDate.of(1900, 1, 1),
    ): FxRate?
}
