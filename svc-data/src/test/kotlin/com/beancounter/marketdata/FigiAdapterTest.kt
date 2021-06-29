package com.beancounter.marketdata

import com.beancounter.common.model.Market
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.figi.FigiAdapter
import com.beancounter.marketdata.assets.figi.FigiAsset
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Test Bloomberg Figi transformations.
 */
class FigiAdapterTest {
    @Test
    fun is_CodePreserved() {
        val figiAdapter = FigiAdapter()
        val figiAsset = FigiAsset("BRK", "BRK/B", "Mutual Fund")
        val asset = figiAdapter.transform(
            Market("TEST", USD),
            "BRK.B",
            figiAsset
        )
        Assertions.assertThat(asset)
            .hasFieldOrPropertyWithValue("name", "BRK")
            .hasFieldOrPropertyWithValue("code", "BRK.B")
            .hasFieldOrPropertyWithValue("category", "Mutual Fund")
    }
}
