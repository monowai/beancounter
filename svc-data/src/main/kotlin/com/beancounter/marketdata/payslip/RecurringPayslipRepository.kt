package com.beancounter.marketdata.payslip

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.Optional

/**
 * CRUD repo for [RecurringPayslip]. User-facing reads are owner-scoped.
 */
interface RecurringPayslipRepository : CrudRepository<RecurringPayslip, String> {
    fun findByOwnerId(ownerId: String): Optional<RecurringPayslip>

    /** Rows whose next payslip has arrived. */
    fun findByNextPayDateLessThanEqual(date: LocalDate): List<RecurringPayslip>

    /** Stop, offboarding: bulk, idempotent delete of the owner's row. */
    @Modifying(flushAutomatically = true)
    @Query("delete from RecurringPayslip p where p.ownerId = :ownerId")
    fun deleteByOwnerId(
        @Param("ownerId") ownerId: String
    ): Int

    /** Required before deleting a portfolio: the recurring_payslip -> portfolio FK is NO ACTION. */
    @Modifying(flushAutomatically = true)
    @Query("delete from RecurringPayslip p where p.portfolioId = :portfolioId")
    fun deleteByPortfolioId(
        @Param("portfolioId") portfolioId: String
    ): Int
}