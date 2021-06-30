package com.beancounter.common.contracts

import com.beancounter.common.model.Portfolio
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * Portfolio response contract.
 */
data class PortfolioResponse @ConstructorBinding constructor(override var data: Portfolio) : Payload<Portfolio>
