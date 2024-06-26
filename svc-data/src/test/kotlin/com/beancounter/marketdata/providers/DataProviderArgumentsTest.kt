package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.AAPL
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.ProviderArguments.Companion.getInstance
import com.beancounter.marketdata.providers.cash.CashProviderService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDate

/**
 * ProviderArguments could get quite complex.  Here are some logic checks to assert various states
 * of being based on batch sizes.
 */
internal class DataProviderArgumentsTest {
    private val aapl = getTestAsset(NASDAQ, AAPL.code)
    private val msft = getTestAsset(NASDAQ, MSFT.code)
    private val intc = getTestAsset(NASDAQ, "INTC")
    private val twee = "TWEE"

    @Test
    fun is_BatchOfOne() {
        val providerArguments = ProviderArguments(TestConfig(1))
        providerArguments.addAsset(aapl, DateUtils.TODAY)
        providerArguments.addAsset(msft, DateUtils.TODAY)
        providerArguments.addAsset(intc, DateUtils.TODAY)
        val batch: Map<Int, String?> = providerArguments.batch
        assertThat(batch)
            .containsOnlyKeys(0, 1, 2)
            .containsValues(aapl.code, msft.code, intc.code)
    }

    @Test
    fun is_BatchOfTwo() {
        val providerArguments = ProviderArguments(TestConfig(2))
        providerArguments.addAsset(aapl, DateUtils.TODAY)
        providerArguments.addAsset(msft, DateUtils.TODAY)
        providerArguments.addAsset(intc, DateUtils.TODAY)
        val batch: Map<Int, String?> = providerArguments.batch
        assertThat(batch)
            .containsOnlyKeys(0, 1)
            .containsValue("${aapl.code},${msft.code}")
            .containsValue(intc.code)
    }

    @Test
    fun is_BatchOfThree() {
        val providerArguments = ProviderArguments(TestConfig(3))
        providerArguments.addAsset(aapl, DateUtils.TODAY)
        providerArguments.addAsset(msft, DateUtils.TODAY)
        providerArguments.addAsset(intc, DateUtils.TODAY)
        val batch: Map<Int, String?> = providerArguments.batch
        assertThat(batch)
            .containsOnlyKeys(0)
            .containsValue("${aapl.code},${msft.code},${intc.code}")
    }

    @Test
    fun is_SplitByMarket() {
        val assets: MutableCollection<PriceAsset> = ArrayList()
        val marketA = "AAA"
        val code = "ABC"
        assets.add(
            PriceAsset(
                marketA,
                code,
                getTestAsset(Market(marketA), code),
            ),
        )
        val marketB = "BBB"
        assets.add(
            PriceAsset(
                marketB,
                code,
                getTestAsset(Market(marketB), code),
            ),
        )
        val marketC = "CCC"
        assets.add(
            PriceAsset(
                marketC,
                code,
                getTestAsset(Market(marketC), code),
            ),
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
        val assetInputs: MutableCollection<PriceAsset> =
            arrayListOf(
                PriceAsset(NYSE.code, twee, assetId = twee),
            )
        val splitResults: Map<MarketDataPriceProvider, MutableCollection<Asset>> =
            providerUtils.splitProviders(assetInputs)
        assertThat(splitResults).hasSize(1)
        splitResults.forEach {
            assertThat(it.value).hasSize(1)
        }
    }

    @Test
    fun inactiveAssetsExcluded() {
        val providerUtils = getProviderUtils(NYSE)
        val notActive = "Not Active"
        val priceAsset =
            PriceAsset(
                NYSE.code,
                notActive,
                Asset.of(AssetInput(NYSE.code, notActive), NYSE, Status.Inactive),
                notActive,
            )
        val assetInputs: MutableCollection<PriceAsset> = arrayListOf(priceAsset)
        val splitResults: Map<MarketDataPriceProvider, MutableCollection<Asset>> =
            providerUtils.splitProviders(assetInputs)
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
        Mockito.`when`(mdFactory.getMarketDataProvider(market)).thenReturn(CashProviderService())
        return ProviderUtils(mdFactory, marketService)
    }

    private class TestConfig(private val batchSize: Int) : DataProviderConfig {
        private val dateUtils = DateUtils()

        override fun getBatchSize(): Int {
            return batchSize
        }

        override fun getMarketDate(
            market: Market,
            date: String,
            currentMode: Boolean,
        ): LocalDate {
            return if (dateUtils.isToday(date)) {
                dateUtils.date
            } else {
                dateUtils.getFormattedDate(date)
            }
        }

        override fun getPriceCode(asset: Asset): String {
            return asset.code
        }
    }
}
