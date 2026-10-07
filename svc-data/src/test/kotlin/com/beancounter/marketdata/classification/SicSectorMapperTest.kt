package com.beancounter.marketdata.classification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * SIC major-group/division ranges collapse onto the 11 canonical sector names that
 * [SectorNormalizer] emits. Boundaries are the interesting bit: a range edge landing in the
 * wrong bucket silently misfiles every company in that group.
 */
class SicSectorMapperTest {
    private val mapper = SicSectorMapper()

    @ParameterizedTest(name = "SIC {0} maps to {1}")
    @CsvSource(
        "3571, Information Technology",
        "7372, Information Technology",
        "3674, Information Technology",
        "2834, Health Care",
        "8731, Health Care",
        "3841, Health Care",
        "6022, Financials",
        "6770, Financials",
        "6798, Real Estate",
        "6512, Real Estate",
        "4911, Utilities",
        "1311, Energy",
        "2911, Energy",
        "5411, Consumer Staples",
        "2080, Consumer Staples",
        "5311, Consumer Discretionary",
        "3711, Consumer Discretionary",
        "4813, Communication Services",
        "7812, Communication Services",
        "3312, Materials",
        "2810, Materials",
        "3720, Industrials",
        "4512, Industrials",
        "1531, Industrials"
    )
    fun `should map a SIC code onto a canonical sector`(
        sic: String,
        expected: String
    ) {
        assertThat(mapper.toSector(sic)).isEqualTo(expected)
    }

    @Test
    fun `should respect range boundaries between mining and construction`() {
        assertThat(mapper.toSector("1000")).isEqualTo("Materials")
        assertThat(mapper.toSector("1499")).isEqualTo("Materials")
        assertThat(mapper.toSector("1500")).isEqualTo("Industrials")
        assertThat(mapper.toSector("1799")).isEqualTo("Industrials")
    }

    @Test
    fun `should return null for unknown, blank or non-numeric SIC codes`() {
        assertThat(mapper.toSector("9995")).isNull()
        assertThat(mapper.toSector("0000")).isNull()
        assertThat(mapper.toSector("")).isNull()
        assertThat(mapper.toSector("ABCD")).isNull()
        assertThat(mapper.toSector(null)).isNull()
    }

    @Test
    fun `should only ever emit canonical GICS sector names`() {
        val emitted = (100..9999).mapNotNull { mapper.toSector(it.toString().padStart(4, '0')) }.toSet()

        assertThat(emitted).isNotEmpty.isSubsetOf(SectorNormalizer.GICS_SECTORS)
    }
}