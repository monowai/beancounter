package com.beancounter.common.model

import jakarta.persistence.Entity
import jakarta.persistence.Id

/**
 * Currency identified by ISO code.
 */
@Entity
data class Currency(
    @Id val code: String,
    var name: String = "Dollar",
    var symbol: String = "$"
) {
    override fun equals(other: Any?): Boolean = other is Currency && code == other.code

    override fun hashCode(): Int = code.hashCode()

    override fun toString(): String = code
}