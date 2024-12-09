package com.beancounter.common.contracts

import com.beancounter.common.input.TrnInput

/**
 * supports import request.  associate the supplied TrnInput with the portfolio
 */
data class TrnRequest(
    var portfolioId: String,
    override var data: Array<TrnInput>
) : Payload<Array<TrnInput>> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrnRequest

        if (portfolioId != other.portfolioId) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = portfolioId.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}