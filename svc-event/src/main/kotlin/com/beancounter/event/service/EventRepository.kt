package com.beancounter.event.service

import com.beancounter.common.event.CorporateEvent
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate
import java.util.Optional

/**
 * CorporateEvent, or Corporate Actions, record details to apply to a position.
 * This interface exposes the persistent state and associated queries.
 */
interface EventRepository : CrudRepository<CorporateEvent, String> {
    fun findByAssetIdAndRecordDate(assetId: String, recordDate: LocalDate): Optional<CorporateEvent>
    fun findByAssetId(assetId: String): Collection<CorporateEvent>

    @Query(
        "select e from CorporateEvent e " +
            "where e.recordDate >= ?1 and e.recordDate <= ?2 order by e.recordDate asc "
    )
    fun findByDateRange(start: LocalDate?, end: LocalDate?): Collection<CorporateEvent>

    @Query(
        "select e from CorporateEvent e " +
            "where e.recordDate >= ?1 order by e.recordDate desc "
    )
    fun findByStartDate(start: LocalDate): Collection<CorporateEvent>
}
