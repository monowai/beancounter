package com.beancounter.marketdata.classification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for SectorNormalizer.
 * Verifies that sector names from various sources are normalized to canonical GICS-aligned names.
 */
class SectorNormalizerTest {
    private val sectorNormalizer = SectorNormalizer()

    @ParameterizedTest
    @CsvSource(
        "TECHNOLOGY, Information Technology",
        "INFORMATION TECHNOLOGY, Information Technology",
        "technology, Information Technology",
        "Technology, Information Technology"
    )
    fun `normalizes technology sector variations`(
        input: String,
        expected: String
    ) {
        assertThat(sectorNormalizer.normalize(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "HEALTH CARE, Health Care",
        "HEALTHCARE, Health Care",
        "health care, Health Care",
        "Healthcare, Health Care"
    )
    fun `normalizes health care sector variations`(
        input: String,
        expected: String
    ) {
        assertThat(sectorNormalizer.normalize(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "FINANCIALS, Financials",
        "FINANCIAL, Financials",
        "FINANCIAL SERVICES, Financials",
        "financials, Financials"
    )
    fun `normalizes financials sector variations`(
        input: String,
        expected: String
    ) {
        assertThat(sectorNormalizer.normalize(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "CONSUMER DISCRETIONARY, Consumer Discretionary",
        "CONSUMER CYCLICAL, Consumer Discretionary"
    )
    fun `normalizes consumer discretionary sector variations`(
        input: String,
        expected: String
    ) {
        assertThat(sectorNormalizer.normalize(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "CONSUMER STAPLES, Consumer Staples",
        "CONSUMER DEFENSIVE, Consumer Staples"
    )
    fun `normalizes consumer staples sector variations`(
        input: String,
        expected: String
    ) {
        assertThat(sectorNormalizer.normalize(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "INDUSTRIALS, Industrials",
        "INDUSTRIAL, Industrials"
    )
    fun `normalizes industrials sector variations`(
        input: String,
        expected: String
    ) {
        assertThat(sectorNormalizer.normalize(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "MATERIALS, Materials",
        "BASIC MATERIALS, Materials"
    )
    fun `normalizes materials sector variations`(
        input: String,
        expected: String
    ) {
        assertThat(sectorNormalizer.normalize(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "COMMUNICATION SERVICES, Communication Services",
        "TELECOMMUNICATIONS, Communication Services",
        "TELECOMMUNICATION SERVICES, Communication Services"
    )
    fun `normalizes communication services sector variations`(
        input: String,
        expected: String
    ) {
        assertThat(sectorNormalizer.normalize(input)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "ENERGY, Energy",
        "UTILITIES, Utilities",
        "REAL ESTATE, Real Estate"
    )
    fun `normalizes standard GICS sectors`(
        input: String,
        expected: String
    ) {
        assertThat(sectorNormalizer.normalize(input)).isEqualTo(expected)
    }

    @Test
    fun `returns title case for unknown sectors`() {
        assertThat(sectorNormalizer.normalize("SOME UNKNOWN SECTOR"))
            .isEqualTo("Some Unknown Sector")
        assertThat(sectorNormalizer.normalize("custom sector"))
            .isEqualTo("Custom Sector")
    }

    @Test
    fun `handles whitespace in input`() {
        assertThat(sectorNormalizer.normalize("  TECHNOLOGY  "))
            .isEqualTo("Information Technology")
        assertThat(sectorNormalizer.normalize("  UNKNOWN  "))
            .isEqualTo("Unknown")
    }

    @Test
    fun `GICS_SECTORS contains all 11 standard sectors`() {
        assertThat(SectorNormalizer.GICS_SECTORS)
            .hasSize(11)
            .contains(
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
}