package com.beancounter.common.model

import javax.persistence.Entity
import javax.persistence.Id

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

        if (code != other.code) return false

        return true
    }

    override fun hashCode(): Int {
        return code.hashCode()
    }

    override fun toString(): String {
        return code
    }
}
