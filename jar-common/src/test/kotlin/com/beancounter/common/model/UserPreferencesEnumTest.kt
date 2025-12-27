package com.beancounter.common.model

import com.beancounter.common.contracts.UserPreferencesRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Contract tests to verify enum values match what bc-view frontend expects.
 * These tests ensure the API contract between frontend and backend is maintained.
 *
 * Frontend sends these exact string values - changing them would break the API.
 */
class UserPreferencesEnumTest {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    @Test
    fun `ValueInPreference enum values match frontend expectations`() {
        // bc-view sends these exact values from types/constants.ts
        assertThat(ValueInPreference.valueOf("PORTFOLIO")).isEqualTo(ValueInPreference.PORTFOLIO)
        assertThat(ValueInPreference.valueOf("BASE")).isEqualTo(ValueInPreference.BASE)
        assertThat(ValueInPreference.valueOf("TRADE")).isEqualTo(ValueInPreference.TRADE)

        // Verify all expected values exist
        assertThat(ValueInPreference.entries).containsExactlyInAnyOrder(
            ValueInPreference.PORTFOLIO,
            ValueInPreference.BASE,
            ValueInPreference.TRADE
        )
    }

    @Test
    fun `GroupByPreference enum values match frontend expectations`() {
        // bc-view sends these exact values from types/constants.ts GROUP_BY_API_VALUES
        assertThat(GroupByPreference.valueOf("ASSET_CLASS")).isEqualTo(GroupByPreference.ASSET_CLASS)
        assertThat(GroupByPreference.valueOf("SECTOR")).isEqualTo(GroupByPreference.SECTOR)
        assertThat(GroupByPreference.valueOf("MARKET_CURRENCY")).isEqualTo(GroupByPreference.MARKET_CURRENCY)
        assertThat(GroupByPreference.valueOf("MARKET")).isEqualTo(GroupByPreference.MARKET)

        // Verify all expected values exist
        assertThat(GroupByPreference.entries).containsExactlyInAnyOrder(
            GroupByPreference.ASSET_CLASS,
            GroupByPreference.SECTOR,
            GroupByPreference.MARKET_CURRENCY,
            GroupByPreference.MARKET
        )
    }

    @Test
    fun `enum names serialize correctly for JSON API responses`() {
        // These are the exact strings that will be serialized to JSON
        assertThat(ValueInPreference.PORTFOLIO.name).isEqualTo("PORTFOLIO")
        assertThat(ValueInPreference.BASE.name).isEqualTo("BASE")
        assertThat(ValueInPreference.TRADE.name).isEqualTo("TRADE")

        assertThat(GroupByPreference.ASSET_CLASS.name).isEqualTo("ASSET_CLASS")
        assertThat(GroupByPreference.SECTOR.name).isEqualTo("SECTOR")
        assertThat(GroupByPreference.MARKET_CURRENCY.name).isEqualTo("MARKET_CURRENCY")
        assertThat(GroupByPreference.MARKET.name).isEqualTo("MARKET")
    }

    @Test
    fun `deserialize UserPreferencesRequest from frontend JSON`() {
        // This is the exact JSON that bc-view sends when saving settings
        val json =
            """
            {
                "preferredName": "Test User",
                "defaultHoldingsView": "TABLE",
                "defaultValueIn": "BASE",
                "defaultGroupBy": "SECTOR",
                "baseCurrencyCode": "NZD"
            }
            """.trimIndent()

        val request = objectMapper.readValue(json, UserPreferencesRequest::class.java)

        assertThat(request.preferredName).isEqualTo("Test User")
        assertThat(request.defaultHoldingsView).isEqualTo(HoldingsView.TABLE)
        assertThat(request.defaultValueIn).isEqualTo(ValueInPreference.BASE)
        assertThat(request.defaultGroupBy).isEqualTo(GroupByPreference.SECTOR)
        assertThat(request.baseCurrencyCode).isEqualTo("NZD")
    }

    @Test
    fun `deserialize all GroupByPreference values from frontend JSON`() {
        val testCases =
            listOf(
                "ASSET_CLASS" to GroupByPreference.ASSET_CLASS,
                "SECTOR" to GroupByPreference.SECTOR,
                "MARKET_CURRENCY" to GroupByPreference.MARKET_CURRENCY,
                "MARKET" to GroupByPreference.MARKET
            )

        testCases.forEach { (jsonValue, expectedEnum) ->
            val json = """{"defaultGroupBy": "$jsonValue"}"""
            val request = objectMapper.readValue(json, UserPreferencesRequest::class.java)
            assertThat(request.defaultGroupBy)
                .describedAs("JSON value '$jsonValue' should deserialize to $expectedEnum")
                .isEqualTo(expectedEnum)
        }
    }

    @Test
    fun `deserialize all ValueInPreference values from frontend JSON`() {
        val testCases =
            listOf(
                "PORTFOLIO" to ValueInPreference.PORTFOLIO,
                "BASE" to ValueInPreference.BASE,
                "TRADE" to ValueInPreference.TRADE
            )

        testCases.forEach { (jsonValue, expectedEnum) ->
            val json = """{"defaultValueIn": "$jsonValue"}"""
            val request = objectMapper.readValue(json, UserPreferencesRequest::class.java)
            assertThat(request.defaultValueIn)
                .describedAs("JSON value '$jsonValue' should deserialize to $expectedEnum")
                .isEqualTo(expectedEnum)
        }
    }
}
