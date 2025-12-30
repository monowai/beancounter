package com.beancounter.common.contracts

import com.beancounter.common.input.TrnInput

/**
 * Request to import transactions for a portfolio.
 */
data class TrnRequest(
    val portfolioId: String,
    override val data: List<TrnInput>
) : Payload<List<TrnInput>>