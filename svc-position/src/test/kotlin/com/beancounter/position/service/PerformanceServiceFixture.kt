package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.TrnService
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.cache.NoOpPerformanceCacheService
import com.beancounter.position.cache.PerformanceCacheService
import com.beancounter.position.cache.PerformanceSnapshotCache
import com.beancounter.position.irr.IrrCalculator
import com.beancounter.position.irr.TwrCalculator

/**
 * The one place tests wire a [PerformanceService]: its collaborators change shape
 * as performance concepts are extracted, the test inputs do not.
 */
internal fun performanceService(
    trnService: TrnService,
    accumulator: Accumulator,
    priceService: PriceService,
    fxRateService: FxService,
    tokenService: TokenService,
    cacheService: PerformanceCacheService = NoOpPerformanceCacheService(),
    twrCalculator: TwrCalculator = TwrCalculator(),
    dateUtils: DateUtils = DateUtils()
): PerformanceService {
    val boundary = TwrBoundary()
    return PerformanceService(
        inputs = PerformanceInputs(trnService, priceService, fxRateService, tokenService, boundary),
        replayer = SnapshotReplayer(accumulator, boundary),
        snapshotCache = PerformanceSnapshotCache(cacheService),
        twrCalculator = twrCalculator,
        aggregateComposer = AggregatePerformanceComposer(IrrCalculator(minHoldingDays = 365, dateUtils = dateUtils)),
        dateUtils = dateUtils
    )
}