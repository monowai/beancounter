package com.beancounter.common

import com.beancounter.common.TestMarkets.Companion.NZD
import com.beancounter.common.TestMarkets.Companion.USD
import com.beancounter.common.input.AssetInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RealEstateInputTest {
    @Test
    fun is_RealEstateDefaulting() {
        val apartment = AssetInput.toRealEstate(NZD, "Apartment")
        val house = AssetInput.toRealEstate(USD, "House")
        assertThat(apartment)
            .hasFieldOrPropertyWithValue("market", "PRIVATE")
            .hasFieldOrPropertyWithValue("category", "RE")
            .hasFieldOrPropertyWithValue("name", "Apartment")
            .hasFieldOrPropertyWithValue("code", "${NZD.code}.RE")

        assertThat(house)
            .hasFieldOrPropertyWithValue("market", "PRIVATE")
            .hasFieldOrPropertyWithValue("category", "RE")
            .hasFieldOrPropertyWithValue("name", "House")
            .hasFieldOrPropertyWithValue("code", "${USD.code}.RE")

    }
}