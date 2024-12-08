package com.beancounter.common.model

/**
 * Consistent representation of a currency pairing
 */
data class IsoCurrencyPair(
    val from: String,
    val to: String,
) {
    override fun toString(): String = "$from:$to"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IsoCurrencyPair

        if (from != other.from) return false
        return to == other.to
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        return result
    }

    companion object {
        @JvmStatic
        fun toPair(
            from: Currency,
            to: Currency,
        ): IsoCurrencyPair? {
            if (from.code == to.code) {
                return null
            }
            return IsoCurrencyPair(
                from.code,
                to.code,
            )
        }
    }
}
