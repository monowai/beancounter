package com.beancounter.marketdata.providers

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Coroutine scope dedicated to price-history backfills. Bound to a
 * `limitedParallelism` slice of `Dispatchers.IO` so the backfill workload
 * can't starve other I/O paths in svc-data, and tunable via
 * `price.backfill.workers` so a chart-traffic spike can be absorbed
 * without a redeploy. A `SupervisorJob` keeps a single failed backfill
 * from cancelling sibling work, and the `CoroutineName` tags log/MDC
 * output for easy filtering.
 */
@Configuration
class PriceBackfillScopeConfig(
    @Value("\${price.backfill.workers:4}") private val workers: Int
) {
    @Bean("priceBackfillScope")
    @OptIn(ExperimentalCoroutinesApi::class)
    fun priceBackfillScope(): CoroutineScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO.limitedParallelism(workers) +
                CoroutineName("price-backfill")
        )
}