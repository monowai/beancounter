package com.beancounter.marketdata.fx.fxrates

import com.beancounter.common.model.FxRate

/**
 * Interface for FX rate providers. Implementations fetch exchange rates
 * from different sources (Frankfurter, exchangeratesapi.io, etc.)
 */
interface FxRateProvider {
    /**
     * Provider identifier for logging and debugging
     */
    val id: String

    /**
     * Fetch FX rates for the given date
     * @param asAt the date to fetch rates for (yyyy-MM-dd format)
     * @return list of FxRate objects, or empty list if unavailable
     */
    fun getRates(asAt: String): List<FxRate>
}