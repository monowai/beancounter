package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.marketdata.Constants.Companion.INDEX_MARKET
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class AlphaIndexBackFillTest {
    private val proxy = mock(AlphaProxy::class.java)
    private val adapter = mock(AlphaPriceAdapter::class.java)
    private val service =
        AlphaPriceService(AlphaConfig()).also {
            it.setAlphaHelpers(proxy, adapter)
            // apiKey is lateinit but unused by mocked proxy; set via reflection
            val field = AlphaPriceService::class.java.getDeclaredField("apiKey")
            field.isAccessible = true
            field.set(it, "demo")
        }

    private val emptyHistoric =
        """{"Meta Data":{"2. Symbol":"X"},"Time Series (Daily)":{}}"""
    private val emptyAdjusted =
        """{"Meta Data":{"2. Symbol":"X"},"Time Series (Daily)":{}}"""

    @Test
    fun `backFill uses getHistoric for INDEX category asset`() {
        val sp500 =
            Asset(
                code = "^GSPC",
                id = "^GSPC",
                market = INDEX_MARKET,
                priceSymbol = "^GSPC",
                category = AssetCategory.INDEX
            )
        whenever(proxy.getHistoric(eq("^GSPC"), any())).thenReturn(emptyHistoric)

        val response = service.backFill(sp500)

        verify(proxy).getHistoric(eq("^GSPC"), any())
        verify(proxy, never()).getAdjusted(any(), any())
        assertThat(response.data).isEmpty()
    }

    @Test
    fun `backFill uses getAdjusted for non-INDEX category asset`() {
        val msft =
            Asset(
                code = "MSFT",
                id = "MSFT",
                market = NASDAQ,
                category = "EQUITY"
            )
        whenever(proxy.getAdjusted(eq("MSFT"), any())).thenReturn(emptyAdjusted)

        service.backFill(msft)

        verify(proxy).getAdjusted(eq("MSFT"), any())
        verify(proxy, never()).getHistoric(any(), any())
    }
}