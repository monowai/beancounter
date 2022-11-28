package com.beancounter.common.contracts

import com.beancounter.common.model.Portfolio

/**
 * Portfolio response contract.
 */
data class PortfolioResponse(override var data: Portfolio) : Payload<Portfolio>
