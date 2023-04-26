package com.beancounter.marketdata.assets

import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.apartment
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.trn.realestate.RealEstateServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal

internal class RealEstateImpactTest {
    private val assetService = Mockito.mock(AssetService::class.java)
    private val realEstateServices = RealEstateServices(assetService, Mockito.mock(CurrencyService::class.java))
    @Test
    fun is_ImpactCalculatedForBuy() {
        val trnInput = TrnInput(
            callerRef = CallerRef(),
            assetId = apartment.id,
            trnType = TrnType.BUY,
            cashCurrency = Constants.NZD.code,
            tradeAmount = BigDecimal("10000"),
            price = BigDecimal("10000"),
        )
        Mockito.`when`(assetService.find(AssetInput.realEstate, Constants.NZD.code))
            .thenReturn(apartment)

        assertThat(realEstateServices.getImpact(trnInput)).isEqualTo(BigDecimal("-10000.00"))
    }
    @Test
    fun is_ImpactCalculatedForSell() {
        val trnInput = TrnInput(
            callerRef = CallerRef(),
            assetId = apartment.id,
            trnType = TrnType.SELL,
            cashCurrency = Constants.NZD.code,
            tradeAmount = BigDecimal("20000"),
            price = BigDecimal("20000"),
        )
        Mockito.`when`(assetService.find(AssetInput.realEstate, Constants.NZD.code))
            .thenReturn(apartment)

        assertThat(realEstateServices.getImpact(trnInput)).isEqualTo(BigDecimal("20000.00"))
    }
}