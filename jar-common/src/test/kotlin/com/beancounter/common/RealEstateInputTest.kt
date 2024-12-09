package com.beancounter.common

import com.beancounter.common.TestMarkets.Companion.NZD
import com.beancounter.common.TestMarkets.Companion.USD
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.AssetCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val OFF_MARKET = "OFFM"

/**
 * Verify we can create RealEstate assets.
 */
class RealEstateInputTest {
    @Test
    fun is_RealEstateDefaulting() {
        val apartment =
            AssetInput.toRealEstate(
                NZD,
                "APT",
                "Apartment",
                "test-user"
            )
        val house =
            AssetInput.toRealEstate(
                USD,
                "HOUSE",
                "House",
                "test-user"
            )
        assertThat(apartment)
            .hasFieldOrPropertyWithValue(
                "market",
                OFF_MARKET
            ).hasFieldOrPropertyWithValue(
                "category",
                AssetCategory.RE
            ).hasFieldOrPropertyWithValue(
                "name",
                "Apartment"
            ).hasFieldOrPropertyWithValue(
                "code",
                apartment.code
            )

        assertThat(house)
            .hasFieldOrPropertyWithValue(
                "market",
                OFF_MARKET
            ).hasFieldOrPropertyWithValue(
                "category",
                AssetCategory.RE
            ).hasFieldOrPropertyWithValue(
                "name",
                "House"
            ).hasFieldOrPropertyWithValue(
                "code",
                house.code
            )
    }
}