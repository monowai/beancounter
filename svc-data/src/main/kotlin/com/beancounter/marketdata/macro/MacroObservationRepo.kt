package com.beancounter.marketdata.macro

import org.springframework.data.repository.CrudRepository
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
}