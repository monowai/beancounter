package com.beancounter.common.contracts

import com.beancounter.common.input.PortfolioInput

/**
 * Contract to process the supplied PortfolioInput contract objects.
 */
data class PortfoliosRequest(
    var data: Collection<PortfolioInput>
)