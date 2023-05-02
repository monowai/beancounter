package com.beancounter.common

import com.beancounter.common.TestMarkets.Companion.NZD
import com.beancounter.common.TestMarkets.Companion.USD
import com.beancounter.common.input.AssetInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val PRIVATE = "PRIVATE"

/**
 * Verify we can create RealEstate assets.
 */
class RealEstateInputTest {
    @Test
    fun is_RealEstateDefaulting() {
        val apartment = AssetInput.toRealEstate(NZD, "Apartment")
        val house = AssetInput.toRealEstate(USD, "House")
        assertThat(apartment)
            .hasFieldOrPropertyWithValue("market", PRIVATE)
            .hasFieldOrPropertyWithValue("category", AssetInput.realEstate)
            .hasFieldOrPropertyWithValue("name", "Apartment")
            .hasFieldOrPropertyWithValue("code", "${NZD.code}.${AssetInput.realEstate}")

        assertThat(house)
            .hasFieldOrPropertyWithValue("market", PRIVATE)
            .hasFieldOrPropertyWithValue("category", AssetInput.realEstate)
            .hasFieldOrPropertyWithValue("name", "House")
            .hasFieldOrPropertyWithValue("code", "${USD.code}.${AssetInput.realEstate}")
    }
}
