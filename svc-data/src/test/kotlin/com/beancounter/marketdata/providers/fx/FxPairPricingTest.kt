package com.beancounter.marketdata.providers.fx

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetSearchService
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.figi.FigiGateway
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.FxRateRepository
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.fx.fxrates.FxProviderService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.watchlist.AddWatchlistItemRequest
import com.beancounter.marketdata.watchlist.WatchlistService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * A currency pair is priced through the ordinary price path from the stored USD->X rates —
 * the rates valuations convert with — rather than a market-data vendor.
 */
@SpringMvcDbTest
internal class FxPairPricingTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    // No rate is fetched: every date the test asks for is already stored.
    @MockitoBean
    private lateinit var fxProviderService: FxProviderService

    @MockitoBean
    private lateinit var figiGateway: FigiGateway

    @Autowired
    private lateinit var fxRateRepository: FxRateRepository

    @Autowired
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var marketService: MarketService

    @Autowired
    private lateinit var mdFactory: MdFactory

    @Autowired
    private lateinit var assetSearchService: AssetSearchService

    @Autowired
    private lateinit var watchlistService: WatchlistService

    @Autowired
    private lateinit var systemUserRepository: SystemUserRepository

    @Autowired
    private lateinit var fxRateService: FxRateService

    private val thursday = LocalDate.of(2024, 1, 18)
    private val friday = LocalDate.of(2024, 1, 19)
    private val usdNzd = BigDecimal("1.620000")
    private val usdSgd = BigDecimal("1.340000")

    @BeforeEach
    fun storeRates() {
        val usd = currencyService.getCode("USD")
        val nzd = currencyService.getCode("NZD")
        val sgd = currencyService.getCode("SGD")
        fxRateRepository.saveAll(
            listOf(
                FxRate(from = usd, to = usd, rate = BigDecimal.ONE, date = LocalDate.of(1900, 1, 1)),
                FxRate(from = usd, to = nzd, rate = BigDecimal("1.610000"), date = thursday),
                FxRate(from = usd, to = sgd, rate = BigDecimal("1.330000"), date = thursday),
                FxRate(from = usd, to = nzd, rate = usdNzd, date = friday),
                FxRate(from = usd, to = sgd, rate = usdSgd, date = friday)
            )
        )
    }

    private fun nzdSgd(): Asset =
        assetService
            .handle(
                AssetRequest(
                    mapOf(
                        "NZDSGD" to
                            AssetInput(market = "FX", code = "NZDSGD", currency = "SGD", category = "Currency")
                    )
                )
            ).data
            .getValue("NZDSGD")

    @Test
    fun `should route the FX market to the FX pair provider`() {
        assertThat(mdFactory.getMarketDataProvider(marketService.getMarket("FX")).getId())
            .isEqualTo(FxPairPriceService.ID)
    }

    @Test
    fun `should not route the FX market to EODHD`() {
        assertThat(marketService.getMarket("FX").getAlias("eodhd")).isNull()
    }

    @Test
    fun `should price NZDSGD as SGD per NZD from the stored rates`() {
        val asset = nzdSgd()

        val prices =
            marketDataService
                .getPriceResponse(PriceRequest(friday.toString(), listOf(PriceAsset(asset))))
                .data

        assertThat(prices).hasSize(1)
        val price = prices.single()
        assertThat(price.priceDate).isEqualTo(friday)
        assertThat(price.close)
            .isEqualByComparingTo(usdSgd.divide(usdNzd, 6, RoundingMode.HALF_UP))
    }

    @Test
    fun `should backfill NZDSGD from the stored rate history`() {
        val asset = nzdSgd()

        val history = mdFactory.getMarketDataProvider(asset.market).backFill(asset, thursday)

        // Starts at the from date; the watchlist test may also store today's rates.
        assertThat(history.data.map { it.priceDate }).startsWith(thursday, friday)
        assertThat(history.data.single { it.priceDate == friday }.close)
            .isEqualByComparingTo(usdSgd.divide(usdNzd, 6, RoundingMode.HALF_UP))
    }

    @Test
    fun `should find a pair on an unscoped search without an FX prefix`() {
        val results = assetSearchService.search("NZDSGD", null).data

        assertThat(results.filter { it.market == "FX" }.map { it.symbol }).containsExactly("NZDSGD")
    }

    @Test
    fun `should stamp a pair added to a watchlist without a price at the latest rate`() {
        // The rates for the date a "today" price resolves to; before the FX provider the pair
        // fell through to the Cash provider and was stamped 1.00.
        val rateDate = fxRateService.getDate(DateUtils.TODAY)
        val usd = currencyService.getCode("USD")
        val todayNzd = BigDecimal("1.650000")
        val todaySgd = BigDecimal("1.360000")
        fxRateRepository.saveAll(
            listOf(
                FxRate(from = usd, to = currencyService.getCode("NZD"), rate = todayNzd, date = rateDate),
                FxRate(from = usd, to = currencyService.getCode("SGD"), rate = todaySgd, date = rateDate)
            )
        )
        val owner = systemUserRepository.save(SystemUser(id = "fx-watcher", email = "fx-watcher@test.com"))

        val item = watchlistService.addItem(owner, AddWatchlistItemRequest(assetId = nzdSgd().id))

        val expected = todaySgd.divide(todayNzd, 6, RoundingMode.HALF_UP)
        assertThat(item.addedPrice).isEqualByComparingTo(expected)
        assertThat(item.price).isEqualByComparingTo(expected)
        assertThat(item.priceDate).isEqualTo(rateDate)
    }
}