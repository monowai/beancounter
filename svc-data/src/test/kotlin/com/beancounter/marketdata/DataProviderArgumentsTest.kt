package com.beancounter.marketdata

import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.DataProviderConfig
import com.beancounter.marketdata.providers.ProviderArguments
import com.beancounter.marketdata.providers.ProviderArguments.Companion.getInstance
import com.beancounter.marketdata.providers.ProviderUtils
import com.beancounter.marketdata.providers.mock.MockProviderService
import com.beancounter.marketdata.service.MarketDataProvider
import com.beancounter.marketdata.service.MdFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDate

/**
 * ProviderArguments could get quite complex.  Here are some logic checks to assert various states
 * of being based on batch sizes.
 */
internal class DataProviderArgumentsTest {
    private val aapl = getAsset(NASDAQ, AAPL.code)
    private val msft = getAsset(NASDAQ, MSFT.code)
    private val intc = getAsset(NASDAQ, "INTC")

    @Test
    fun is_BatchOfOne() {
        val providerArguments = ProviderArguments(TestConfig(1))
        providerArguments.addAsset(aapl, DateUtils.today)
        providerArguments.addAsset(msft, DateUtils.today)
        providerArguments.addAsset(intc, DateUtils.today)
        val batch: Map<Int, String?> = providerArguments.batch
        assertThat(batch)
            .containsOnlyKeys(0, 1, 2)
            .containsValues(aapl.code, msft.code, intc.code)
    }

    @Test
    fun is_BatchOfTwo() {
        val providerArguments = ProviderArguments(TestConfig(2))
        providerArguments.addAsset(aapl, DateUtils.today)
        providerArguments.addAsset(msft, DateUtils.today)
        providerArguments.addAsset(intc, DateUtils.today)
        val batch: Map<Int, String?> = providerArguments.batch
        assertThat(batch)
            .containsOnlyKeys(0, 1)
            .containsValue("${aapl.code},${msft.code}")
            .containsValue(intc.code)
    }

    @Test
    fun is_BatchOfThree() {
        val providerArguments = ProviderArguments(TestConfig(3))
        providerArguments.addAsset(aapl, DateUtils.today)
        providerArguments.addAsset(msft, DateUtils.today)
        providerArguments.addAsset(intc, DateUtils.today)
        val batch: Map<Int, String?> = providerArguments.batch
        assertThat(batch)
            .containsOnlyKeys(0)
            .containsValue("${aapl.code},${msft.code},${intc.code}")
    }

    @Test
    fun is_SplitByMarket() {
        val assets: MutableCollection<AssetInput> = ArrayList()
        val marketA = "AAA"
        val code = "ABC"
        assets.add(
            AssetInput(
                marketA, code,
                getAsset(Market(marketA), code)
            )
        )
        val marketB = "BBB"
        assets.add(
            AssetInput(
                marketB, code,
                getAsset(Market(marketB), code)
            )
        )
        val marketC = "CCC"
        assets.add(
            AssetInput(
                marketC, code,
                getAsset(Market(marketC), code)
            )
        )
        val priceRequest = PriceRequest(assets = assets)
        val testConfig = TestConfig(10)
        val providerArguments = getInstance(priceRequest, testConfig)
        val batch: Map<Int, String?> = providerArguments.batch
        assertThat(batch)
            .containsOnlyKeys(0, 1, 2)
    }

    @Test
    fun activeAssetsByProvider() {
        val providerUtils = getProviderUtils(NYSE)
        val assetInputs: MutableCollection<AssetInput> = arrayListOf(AssetInput(NYSE.code, "TWEE"))
        val splitResults: Map<MarketDataProvider, MutableCollection<Asset>> = providerUtils.splitProviders(assetInputs)
        assertThat(splitResults).hasSize(1)
        splitResults.forEach {
            assertThat(it.value).hasSize(1)
        }
    }

    @Test
    fun inactiveAssetsExcluded() {
        val providerUtils = getProviderUtils(NYSE)
        val assetInput = AssetInput(NYSE.code, "Not Active")
        assetInput.resolvedAsset = Asset(assetInput, NYSE, Status.Inactive)
        val assetInputs: MutableCollection<AssetInput> = arrayListOf(assetInput)
        val splitResults: Map<MarketDataProvider, MutableCollection<Asset>> = providerUtils.splitProviders(assetInputs)
        assertThat(splitResults)
            .hasSize(1)

        splitResults.forEach {
            assertThat(it.value).isEmpty()
        }
    }

    private fun getProviderUtils(market: Market): ProviderUtils {
        val marketService = Mockito.mock(MarketService::class.java)
        Mockito.`when`(marketService.getMarket(market.code))
            .thenReturn(market)
        val mdFactory = Mockito.mock(MdFactory::class.java)
        Mockito.`when`(mdFactory.getMarketDataProvider(market)).thenReturn(MockProviderService())
        return ProviderUtils(mdFactory, marketService)
    }

    private class TestConfig(private val batchSize: Int) : DataProviderConfig {
        private val dateUtils = DateUtils()
        override fun getBatchSize(): Int {
            return batchSize
        }

        override fun getMarketDate(market: Market, date: String, currentMode: Boolean): LocalDate {
            return if (dateUtils.isToday(date)) {
                dateUtils.date
            } else dateUtils.getDate(date)
        }

        override fun getPriceCode(asset: Asset): String {
            return asset.code
        }
    }
}
