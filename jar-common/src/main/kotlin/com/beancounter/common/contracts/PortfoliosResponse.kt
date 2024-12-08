package com.beancounter.common.contracts

import com.beancounter.common.model.Portfolio

/**
 * Collection of Portfolios found in response to a PortfoliosRequest.
 */
data class PortfoliosResponse(
    override val data: Collection<Portfolio>,
) : Payload<Collection<Portfolio>>
