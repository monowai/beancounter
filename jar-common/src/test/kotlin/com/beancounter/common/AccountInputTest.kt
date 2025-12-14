package com.beancounter.common

import com.beancounter.common.TestMarkets.Companion.NZD
import com.beancounter.common.TestMarkets.Companion.USD
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.AssetCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val PRIVATE_MARKET = "PRIVATE"

/**
 * Verify we can create user-scoped bank account assets.
 */
class AccountInputTest {
    @Test
    fun is_AccountDefaulting() {
        val currentAccount =
            AssetInput.toAccount(
                USD,
                "USD-SAVINGS",
                "My USD Savings Account",
                "test-user"
            )
        val mortgageAccount =
            AssetInput.toAccount(
                NZD,
                "KB-MORTGAGE",
                "Kiwibank Mortgage",
                "test-user"
            )
        assertThat(currentAccount)
            .hasFieldOrPropertyWithValue(
                "market",
                PRIVATE_MARKET
            ).hasFieldOrPropertyWithValue(
                "category",
                AssetCategory.ACCOUNT
            ).hasFieldOrPropertyWithValue(
                "name",
                "My USD Savings Account"
            ).hasFieldOrPropertyWithValue(
                "code",
                "USD-SAVINGS"
            ).hasFieldOrPropertyWithValue(
                "currency",
                USD.code
            ).hasFieldOrPropertyWithValue(
                "owner",
                "test-user"
            )

        assertThat(mortgageAccount)
            .hasFieldOrPropertyWithValue(
                "market",
                PRIVATE_MARKET
            ).hasFieldOrPropertyWithValue(
                "category",
                AssetCategory.ACCOUNT
            ).hasFieldOrPropertyWithValue(
                "name",
                "Kiwibank Mortgage"
            ).hasFieldOrPropertyWithValue(
                "code",
                "KB-MORTGAGE"
            ).hasFieldOrPropertyWithValue(
                "currency",
                NZD.code
            )
    }
}