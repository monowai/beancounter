package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Status
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Misc tests of provider utilities.
 */
class ProviderUtilsTest {
    @Test
    fun inactiveAssetsIgnored() {
        val providerUtils = ProviderUtils(Mockito.mock(MdFactory::class.java))
        val activeAsset =
            Asset(
                code = "FindMe",
                market = NASDAQ,
                status = Status.Active
            )
        val inactiveAsset =
            Asset(
                code = "IgnoreMe",
                market = NASDAQ,
                status = Status.Inactive
            )
        assertThat(
            providerUtils.getInputs(
                listOf(
                    activeAsset,
                    inactiveAsset
                )
            )
        ).containsExactly(PriceAsset(activeAsset))
    }

    @Test
    fun unresolvedPriceAssetsSkipped() {
        // DATA-4G regression: PriceAsset(market, code) with no resolvedAsset
        // must NOT be fabricated into a phantom Asset(id=code) — that crashed
        // the async persistence path when no DB row existed. Such entries are
        // dropped silently (with a warn) before reaching the provider.
        val providerUtils = ProviderUtils(Mockito.mock(MdFactory::class.java))
        val unresolved = PriceAsset(market = NASDAQ.code, code = "SPY")
        val split = providerUtils.splitProviders(listOf(unresolved))
        assertThat(split).isEmpty()
    }
}