package com.beancounter.marketdata.macro

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * CRUD + nearest-prior finder for [MacroObservation].
 */
interface MacroObservationRepo : CrudRepository<MacroObservation, String> {
    /**
     * The freshest sample for ([series], [metric]) observed at or before [before] — i.e. the
     * observation nearest to (but not more recent than) the trend cutoff.
     */
    fun findFirstBySeriesAndMetricAndObservedAtLessThanEqualOrderByObservedAtDesc(
        series: String,
        metric: String,
        before: LocalDateTime
    ): MacroObservation?

    /**
     * Retention sweep, called from [RateExpectationsService.prune] (invoked by
     * [MacroRefreshSchedule]). A single bulk JPQL delete — never loop-deletes row by row — matching
     * [com.beancounter.marketdata.news.NewsArticleRepo.deleteByPublishedBefore]'s style.
     */
    @Modifying
    @Query("DELETE FROM MacroObservation m WHERE m.observedAt < :threshold")
    fun deleteByObservedAtBefore(
        @Param("threshold") threshold: LocalDateTime
    ): Int
}