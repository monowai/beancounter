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
     * Finds any cached FX rates for a date (regardless of provider).
     * Used for normal valuations - uses whatever rate we have cached.
     *
     * @param date The date to find rates for.
     * @param earlyDate The early date for base rates, default is 1900-01-01.
     * @return A list of FxRate entities that match the criteria.
     */
    @Query(
        """
        select f from FxRate f
        where (f.date >= :date and f.date <= :date)
           or f.date <= :earlyDate
        """
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
     * Finds FX rates for a specific date and provider.
     * Used for provider comparison mode.
     *
     * @param date The date to compare against.
     * @param provider The FX rate provider (e.g., FRANKFURTER, EXCHANGE_RATES_API).
     * @param earlyDate The early date to compare against, default is 1900-01-01.
     * @return A list of FxRate entities that match the criteria.
     */
    @Query(
        """
        select f from FxRate f
        where (f.provider = :provider and f.date >= :date and f.date <= :date)
           or (f.provider = :provider and f.date <= :earlyDate)
        """
    )
    fun findByDateRangeAndProvider(
        date: LocalDate,
        provider: String,
        earlyDate: LocalDate =
            LocalDate.of(
                1900,
                1,
                1
            )
    ): List<FxRate>

    /**
     * Finds any base FX rate for a given currency (regardless of provider).
     * Used for normal valuations.
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

    /**
     * Finds the base FX rate for a given currency and provider.
     * Used for provider comparison mode.
     *
     * @param from The source currency.
     * @param provider The FX rate provider.
     * @param earlyDate The early date to compare against, default is 1900-01-01.
     * @return The base FxRate entity that matches the criteria, or null if not found.
     */
    @Query(
        """
        select f from FxRate f
        where f.from = :from and f.to = :from
          and f.provider = :provider and f.date <= :earlyDate
        """
    )
    fun findBaseRateByProvider(
        from: Currency,
        provider: String,
        earlyDate: LocalDate =
            LocalDate.of(
                1900,
                1,
                1
            )
    ): FxRate?

    /**
     * Finds historical FX rates from the base currency to a target currency.
     * Used for triangulation when charting cross rates.
     *
     * @param baseCurrency The base currency (USD).
     * @param to The target currency.
     * @param startDate The start of the date range.
     * @param endDate The end of the date range.
     * @return A list of FxRate entities ordered by date ascending.
     */
    @Query(
        """
        select f from FxRate f
        where f.from = :baseCurrency and f.to = :to
          and f.date >= :startDate and f.date <= :endDate
        order by f.date asc
        """
    )
    fun findHistoricalRatesFromBase(
        baseCurrency: Currency,
        to: Currency,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<FxRate>
}