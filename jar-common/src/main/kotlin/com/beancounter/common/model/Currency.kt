package com.beancounter.common.model

import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
/**
 * Persistent representation of a Currency.
 */
data class Currency(@Id var code: String, var name: String? = "Dollar", var symbol: String? = "$") {
    constructor(code: String) : this(code, "Dollar", "$")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Currency

        return code == other.code
    }

    override fun hashCode(): Int {
        return code.hashCode()
    }

    override fun toString(): String {
        return code
    }
}
