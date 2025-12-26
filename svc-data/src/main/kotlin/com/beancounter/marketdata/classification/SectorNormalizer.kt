package com.beancounter.marketdata.classification

import org.springframework.stereotype.Component

/**
 * Normalizes sector names from various sources to canonical GICS-aligned names.
 * AlphaVantage returns different formats between OVERVIEW and ETF_PROFILE endpoints.
 */
@Component
class SectorNormalizer {
    companion object {
        private val SECTOR_MAPPINGS =
            mapOf(
                // Technology variations
                "TECHNOLOGY" to "Information Technology",
                "INFORMATION TECHNOLOGY" to "Information Technology",
                // Health Care variations
                "HEALTH CARE" to "Health Care",
                "HEALTHCARE" to "Health Care",
                // Financials variations
                "FINANCIALS" to "Financials",
                "FINANCIAL" to "Financials",
                "FINANCIAL SERVICES" to "Financials",
                // Consumer Discretionary
                "CONSUMER DISCRETIONARY" to "Consumer Discretionary",
                "CONSUMER CYCLICAL" to "Consumer Discretionary",
                // Consumer Staples
                "CONSUMER STAPLES" to "Consumer Staples",
                "CONSUMER DEFENSIVE" to "Consumer Staples",
                // Industrials variations
                "INDUSTRIALS" to "Industrials",
                "INDUSTRIAL" to "Industrials",
                // Energy
                "ENERGY" to "Energy",
                // Materials
                "MATERIALS" to "Materials",
                "BASIC MATERIALS" to "Materials",
                // Real Estate
                "REAL ESTATE" to "Real Estate",
                // Utilities
                "UTILITIES" to "Utilities",
                // Communication Services variations
                "COMMUNICATION SERVICES" to "Communication Services",
                "TELECOMMUNICATIONS" to "Communication Services",
                "TELECOMMUNICATION SERVICES" to "Communication Services"
            )

        /**
         * The 11 GICS sectors in standard order.
         */
        val GICS_SECTORS =
            listOf(
                "Information Technology",
                "Health Care",
                "Financials",
                "Consumer Discretionary",
                "Communication Services",
                "Industrials",
                "Consumer Staples",
                "Energy",
                "Utilities",
                "Real Estate",
                "Materials"
            )
    }

    /**
     * Normalize a sector name to canonical GICS format.
     * Returns the input with title case if no mapping is found.
     */
    fun normalize(sector: String): String {
        val upperSector = sector.trim().uppercase()
        return SECTOR_MAPPINGS[upperSector] ?: toTitleCase(sector)
    }

    private fun toTitleCase(input: String): String =
        input
            .trim()
            .lowercase()
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
}