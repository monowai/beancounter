package com.beancounter.common.model

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Represents a classification framework or standard.
 * Examples: AlphaVantage sector classifications, ICB, internal schemes.
 */
@Entity
@Table(name = "classification_standard")
data class ClassificationStandard(
    @Id
    val id: String,
    val key: String,
    val name: String,
    val version: String = "1.0",
    val provider: String = PROVIDER_ALPHA
) {
    companion object {
        const val ALPHA = "ALPHA"
        const val PROVIDER_ALPHA = "ALPHAVANTAGE"
    }
}