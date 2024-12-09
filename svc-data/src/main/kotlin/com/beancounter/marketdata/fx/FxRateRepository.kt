package com.beancounter.marketdata.fx

import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate

/**
 * Repository interface for accessing FX Rate data.
 * Extends CrudRepository to provide CRUD operations for FxRate entities.
 */
interface FxRateRepository : CrudRepository<FxRate, String> {
    /**
     * Finds FX rates within a specified date range or before a specified early date.
     *
     * @param date The date to compare against.
     * @param earlyDate The early date to compare against, default is 1900-01-01.
     * @return A list of FxRate entities that match the criteria.
     */
    @Query(
        "select f from FxRate f where ((f.date >= :date and f.date <= :date) or f.date <= :earlyDate)"
    )
    fun findByDateRange(
        date: LocalDate,
        earlyDate: LocalDate =
            LocalDate.of(
                1900,
                1,
                1
            )
    ): List<FxRate>

    /**
     * Finds the base FX rate for a given currency pair before a specified early date.
     *
     * @param from The source currency.
     * @param earlyDate The early date to compare against, default is 1900-01-01.
     * @return The base FxRate entity that matches the criteria, or null if not found.
     */
    @Query("select f from FxRate f where f.from = :from and f.to = :from and f.date <= :earlyDate")
    fun findBaseRate(
        from: Currency,
        earlyDate: LocalDate =
            LocalDate.of(
                1900,
                1,
                1
            )
    ): FxRate?
}