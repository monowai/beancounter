package com.beancounter.marketdata.providers.figi

import com.beancounter.common.model.AccountingType
import com.beancounter.common.model.Market
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.AccountingTypeService
import com.beancounter.marketdata.assets.figi.FigiAdapter
import com.beancounter.marketdata.assets.figi.FigiAsset
import com.beancounter.marketdata.currency.CurrencyService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any

/**
 * Test Bloomberg Figi transformations.
 */
class FigiAdapterTest {
    private val accountingTypeService = Mockito.mock(AccountingTypeService::class.java)
    private val currencyService = Mockito.mock(CurrencyService::class.java)

    @Test
    fun is_CodePreserved() {
        Mockito.`when`(currencyService.getCode("USD")).thenReturn(USD)
        Mockito
            .`when`(accountingTypeService.getOrCreate(any(), any(), any(), any()))
            .thenReturn(AccountingType(id = "test", category = "Mutual Fund", currency = USD))
        val figiAdapter = FigiAdapter(accountingTypeService, currencyService)
        val figiAsset =
            FigiAsset(
                "BRK",
                "BRK/B",
                "Mutual Fund"
            )
        val asset =
            figiAdapter.transform(
                Market(
                    "TEST",
                    USD.code
                ),
                "BRK.B",
                figiAsset,
                "ABC"
            )
        Assertions
            .assertThat(asset)
            .hasFieldOrPropertyWithValue(
                "name",
                "BRK"
            ).hasFieldOrPropertyWithValue(
                "code",
                "BRK.B"
            ).hasFieldOrPropertyWithValue(
                "category",
                "Mutual Fund"
            )
    }
}