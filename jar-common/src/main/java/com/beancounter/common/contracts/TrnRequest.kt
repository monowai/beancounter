package com.beancounter.common.contracts

import com.beancounter.common.input.TrnInput

data class TrnRequest(var portfolioId: String, override var data: Array<TrnInput>) : Payload<Array<TrnInput>> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrnRequest

        if (portfolioId != other.portfolioId) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = portfolioId.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
