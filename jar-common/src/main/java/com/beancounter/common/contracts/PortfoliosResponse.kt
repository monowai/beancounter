package com.beancounter.common.contracts

import com.beancounter.common.model.Portfolio
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * Collection of Portfolios found in response to a PortfoliosRequest.
 */
data class PortfoliosResponse @ConstructorBinding constructor(override val data: Collection<Portfolio>) :
    Payload<Collection<Portfolio>>
