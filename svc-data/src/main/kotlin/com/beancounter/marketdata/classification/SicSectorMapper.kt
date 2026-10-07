package com.beancounter.marketdata.classification

import org.springframework.stereotype.Component

/**
 * Maps a four-digit SEC SIC code onto the 11 canonical sector names that [SectorNormalizer]
 * emits, so SEC-sourced classifications sit beside AlphaVantage/EODHD ones in the same buckets.
 *
 * This is an approximation, and knowingly so. SIC (1987) groups by production process while
 * GICS groups by end market, so no range table is exact: SIC 3600-3699 "electronic equipment"
 * straddles Industrials and Information Technology, 8731 "commercial research" is where most
 * biotech files, 6798 REITs sit inside the finance division. The table resolves each range to
 * the sector most of its registrants would land in under GICS; more specific ranges are listed
 * before the division they carve out of and the first match wins. Unknown, blank or
 * non-numeric codes map to null so the caller reports NO_DATA rather than guessing.
 */
@Component
class SicSectorMapper {
    fun toSector(sic: String?): String? {
        val code = sic.orEmpty().trim().toIntOrNull()
        if (code == null) {
            return null
        }
        return RANGES.firstOrNull { (range, _) -> code in range }?.second
    }

    companion object {
        private const val INFORMATION_TECHNOLOGY = "Information Technology"
        private const val HEALTH_CARE = "Health Care"
        private const val FINANCIALS = "Financials"
        private const val CONSUMER_DISCRETIONARY = "Consumer Discretionary"
        private const val COMMUNICATION_SERVICES = "Communication Services"
        private const val INDUSTRIALS = "Industrials"
        private const val CONSUMER_STAPLES = "Consumer Staples"
        private const val ENERGY = "Energy"
        private const val UTILITIES = "Utilities"
        private const val REAL_ESTATE = "Real Estate"
        private const val MATERIALS = "Materials"

        @Suppress("MagicNumber")
        private val RANGES: List<Pair<IntRange, String>> =
            listOf(
                // Division A - agriculture, forestry, fishing
                100..999 to CONSUMER_STAPLES,
                // Division B - mining
                1000..1199 to MATERIALS, // metal mining
                1200..1399 to ENERGY, // coal, oil & gas extraction
                1400..1499 to MATERIALS, // non-metallic minerals
                // Division C - construction
                1500..1799 to INDUSTRIALS,
                // Division D - manufacturing
                2000..2199 to CONSUMER_STAPLES, // food, beverages, tobacco
                2200..2399 to CONSUMER_DISCRETIONARY, // textiles, apparel
                2400..2499 to MATERIALS, // lumber, wood
                2500..2599 to CONSUMER_DISCRETIONARY, // furniture
                2600..2699 to MATERIALS, // paper
                2700..2799 to COMMUNICATION_SERVICES, // printing, publishing
                2830..2839 to HEALTH_CARE, // drugs
                2840..2849 to CONSUMER_STAPLES, // soap, cosmetics
                2800..2899 to MATERIALS, // chemicals
                2900..2999 to ENERGY, // petroleum refining
                3000..3099 to MATERIALS, // rubber, plastics
                3100..3199 to CONSUMER_DISCRETIONARY, // leather
                3200..3399 to MATERIALS, // stone/clay/glass, primary metals
                3400..3499 to INDUSTRIALS, // fabricated metal
                3570..3579 to INFORMATION_TECHNOLOGY, // computer & office equipment
                3500..3599 to INDUSTRIALS, // industrial machinery
                3630..3639 to CONSUMER_DISCRETIONARY, // household appliances
                3600..3659 to INDUSTRIALS, // electrical equipment
                3660..3699 to INFORMATION_TECHNOLOGY, // communications equipment, components
                3710..3719 to CONSUMER_DISCRETIONARY, // motor vehicles
                3750..3759 to CONSUMER_DISCRETIONARY, // motorcycles, bicycles
                3700..3799 to INDUSTRIALS, // aircraft, ships, rail, defence
                3840..3859 to HEALTH_CARE, // medical, dental, ophthalmic
                3860..3879 to CONSUMER_DISCRETIONARY, // photographic, watches
                3800..3899 to INFORMATION_TECHNOLOGY, // measuring & controlling instruments
                3900..3999 to CONSUMER_DISCRETIONARY, // miscellaneous manufacturing
                // Division E - transport, communications, utilities
                4000..4799 to INDUSTRIALS,
                4800..4899 to COMMUNICATION_SERVICES,
                4900..4999 to UTILITIES,
                // Division F - wholesale
                5000..5099 to INDUSTRIALS, // durable goods
                5120..5129 to HEALTH_CARE, // drugs
                5100..5199 to CONSUMER_STAPLES, // non-durable goods
                // Division G - retail
                5400..5499 to CONSUMER_STAPLES, // food stores
                5910..5919 to CONSUMER_STAPLES, // drug stores
                5200..5999 to CONSUMER_DISCRETIONARY,
                // Division H - finance, insurance, real estate
                6500..6599 to REAL_ESTATE,
                6798..6798 to REAL_ESTATE, // REITs
                6000..6799 to FINANCIALS,
                // Division I - services
                7000..7299 to CONSUMER_DISCRETIONARY, // hotels, personal services
                7310..7319 to COMMUNICATION_SERVICES, // advertising
                7370..7379 to INFORMATION_TECHNOLOGY, // software, data processing
                7300..7399 to INDUSTRIALS, // business services
                7500..7699 to CONSUMER_DISCRETIONARY, // auto and miscellaneous repair
                7800..7999 to COMMUNICATION_SERVICES, // motion pictures, amusement
                8000..8099 to HEALTH_CARE, // health services
                8100..8199 to INDUSTRIALS, // legal services
                8200..8399 to CONSUMER_DISCRETIONARY, // education, social services
                8730..8739 to HEALTH_CARE, // commercial research (where biotech files)
                8700..8999 to INDUSTRIALS // engineering, management, miscellaneous services
            )
    }
}