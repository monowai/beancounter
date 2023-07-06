package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Status
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.markets.MarketService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Misc tests of provider utilities.
 */
class ProviderUtilsTest {

    @Test
    fun inactiveAssetsIgnored() {
        val providerUtils = ProviderUtils(
            Mockito.mock(MdFactory::class.java),
            Mockito.mock(MarketService::class.java),
        )
        val activeAsset = Asset(code = "FindMe", market = NASDAQ, status = Status.Active)
        val inactiveAsset = Asset(code = "IgnoreMe", market = NASDAQ, status = Status.Inactive)
        assertThat(
            providerUtils.getInputs(
                listOf(
                    activeAsset,
                    inactiveAsset,
                ),
            ),
        ).containsExactly(PriceAsset(activeAsset))
    }
}
