package com.beancounter.marketdata.providers

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.BulkPriceResponse
import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.utils.BcMvcHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Reproduces the transactional-lookup-poisoning bug class (monowai/beancounter#1088) on the
 * two `/prices` REST entry points.
 *
 * [MarketDataService.getAssetPrices] and [MarketDataService.getBulkAssetPrices] are both
 * `@Transactional(readOnly = true)` and delegate asset resolution to [AssetService], which
 * deliberately treats an unresolvable asset as a normal, non-fatal outcome — it wraps the
 * lookup in `catch (_: Exception)` and simply omits that asset from the response (see
 * `AssetService.resolveAsset` / `resolveAssets`). But the lookup itself runs through
 * [AssetFinder] and/or [com.beancounter.marketdata.markets.MarketService] — both separate
 * `@Service @Transactional` beans — so when they throw ([NotFoundException] for an unknown
 * asset id, or for an unknown market code via `MarketService.getMarket`), Spring's default
 * rollback rule marks the *shared, participating* physical transaction rollback-only before
 * the exception is rethrown to the caller. The `catch` in `AssetService` swallows the
 * exception, so execution proceeds and the response is built successfully — but the
 * transaction is already poisoned. When the outer `@Transactional(readOnly = true)` method
 * that owns the transaction (`getAssetPrices` / `getBulkAssetPrices`) returns, Spring tries to
 * commit, sees the rollback-only flag, and throws `UnexpectedRollbackException` instead —
 * turning an operation that "succeeded" (every asset that *could* resolve, did; the caller
 * just wanted the unresolvable one omitted) into a 500.
 *
 * Only a real `@Transactional` proxy chain (this `@SpringMvcDbTest`, no `@Transactional` on
 * the test itself) can reproduce this — mocking `AssetService`/`AssetFinder` never enters a
 * Spring transaction, so it can't observe rollback-only poisoning or the commit-time
 * `UnexpectedRollbackException`.
 */
@SpringMvcDbTest
class PriceRequestUnresolvedAssetTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @Autowired
    private lateinit var defaultEnricher: DefaultEnricher

    @Autowired
    private lateinit var marketDataRepo: MarketDataRepo

    private lateinit var bcMvcHelper: BcMvcHelper

    @BeforeEach
    fun configure() {
        enrichmentFactory.register(defaultEnricher)
        bcMvcHelper = BcMvcHelper(mockMvc, mockAuthConfig.getUserToken(Constants.systemUser))
        bcMvcHelper.registerUser()
    }

    @Test
    fun `should price the resolvable assets when one asset id is unknown`() {
        // Real, resolvable CASH-market asset with a persisted price.
        val cashAssetInput = AssetInput.toCash(USD, "TXPOISON-BULK-CASH")
        val cashAsset = bcMvcHelper.asset(AssetRequest(cashAssetInput))
        val priceDate = LocalDate.now()
        marketDataRepo.save(
            MarketData(
                asset = cashAsset,
                priceDate = priceDate,
                close = BigDecimal("1.23")
            )
        )

        val bulkPriceRequest =
            BulkPriceRequest(
                dates = listOf(priceDate.toString()),
                assets =
                    listOf(
                        PriceAsset(assetId = cashAsset.id),
                        // Well-formed id that simply doesn't exist — a normal miss, not a
                        // malformed request.
                        PriceAsset(assetId = "txpoison-does-not-exist-1234")
                    )
            )

        val json =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/prices/bulk")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(bcMvcHelper.token))
                        .content(objectMapper.writeValueAsBytes(bulkPriceRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
                .response.contentAsString

        val bulkPriceResponse = objectMapper.readValue(json, BulkPriceResponse::class.java)
        val pricedForDate = bulkPriceResponse.data[priceDate.toString()]
        assertThat(pricedForDate).isNotNull
        assertThat(pricedForDate).hasSize(1)
        assertThat(pricedForDate!!.first().asset.id).isEqualTo(cashAsset.id)
    }

    @Test
    fun `should price the resolvable assets when one market code is unknown`() {
        // Real, resolvable CASH-market asset — priced synthetically by CashProviderService,
        // no external provider call required.
        val cashAssetInput = AssetInput.toCash(USD, "TXPOISON-MKT-CASH")
        val cashAsset = bcMvcHelper.asset(AssetRequest(cashAssetInput))

        val priceRequest =
            PriceRequest(
                assets =
                    listOf(
                        PriceAsset(market = cashAsset.market.code, code = cashAsset.code),
                        // Market code that cannot resolve — MarketService.getMarket throws,
                        // not merely an unknown ticker on a valid market.
                        PriceAsset(market = "ZZ-NOT-A-MARKET", code = "WHATEVER")
                    )
            )

        val json =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/prices")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(bcMvcHelper.token))
                        .content(objectMapper.writeValueAsBytes(priceRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
                .response.contentAsString

        val (data) = objectMapper.readValue(json, PriceResponse::class.java)
        assertThat(data).hasSize(1)
        assertThat(data.iterator().next())
            .hasFieldOrPropertyWithValue("asset.id", cashAsset.id)
            .hasFieldOrPropertyWithValue("close", BigDecimal.ONE)
    }
}