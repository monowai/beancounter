package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.utils.BcMvcHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for the Cash Ladder feature.
 * Cash Ladder shows all transactions that impacted a specific cash asset,
 * not just transactions where the asset itself is the cash asset.
 */
@SpringMvcDbTest
class CashLadderTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    lateinit var assetService: AssetService

    @Autowired
    lateinit var trnService: TrnService

    @Autowired
    lateinit var trnRepository: TrnRepository

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @MockitoBean
    private lateinit var fxClientService: FxRateService

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    private lateinit var bcMvcHelper: BcMvcHelper

    @BeforeEach
    fun setupObjects() {
        assertThat(fxTransactions).isNotNull
        bcMvcHelper =
            BcMvcHelper(
                mockMvc,
                mockAuthConfig.getUserToken(Constants.systemUser)
            )
        bcMvcHelper.registerUser()
        assertThat(figiProxy).isNotNull
        enrichmentFactory.register(DefaultEnricher())
        Mockito
            .`when`(
                fxClientService.getRates(
                    any(),
                    any()
                )
            ).thenReturn(FxResponse(FxPairResults()))
    }

    @Test
    fun `cash ladder returns transactions settled to specific cash asset`() {
        // Create a USD cash asset
        val cashInput = AssetUtils.getCash(USD.code)
        val usdCashAsset =
            assetService
                .handle(
                    AssetRequest(
                        mapOf(Pair(USD.code, cashInput))
                    )
                ).data[USD.code]
        assertThat(usdCashAsset).isNotNull

        // Create an equity asset
        val equity =
            assetService
                .handle(
                    AssetRequest(
                        AssetInput(NYSE.code, MSFT.code),
                        MSFT.code
                    )
                ).data[MSFT.code]
        assertThat(equity).isNotNull

        // Create a portfolio
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "cashLadderTest",
                    currency = USD.code
                )
            )

        // Create a buy transaction that debits the cash asset
        val buyTrn =
            TrnInput(
                callerRef = CallerRef(),
                assetId = equity!!.id,
                cashAssetId = usdCashAsset!!.id,
                cashCurrency = USD.code,
                trnType = TrnType.BUY,
                quantity = BigDecimal("100"),
                price = BigDecimal("150.00"),
                tradeAmount = BigDecimal("15000.00"),
                status = TrnStatus.SETTLED
            )

        // Create a deposit to the cash asset
        val depositTrn =
            TrnInput(
                callerRef = CallerRef(),
                assetId = usdCashAsset.id,
                cashAssetId = usdCashAsset.id,
                cashCurrency = USD.code,
                trnType = TrnType.DEPOSIT,
                tradeAmount = BigDecimal("20000.00"),
                price = BigDecimal.ONE,
                status = TrnStatus.SETTLED
            )

        // Save both transactions
        trnService.save(
            portfolio,
            TrnRequest(portfolio.id, listOf(depositTrn, buyTrn))
        )

        // Test the repository method - find transactions where cashAsset matches
        val cashLadderTrns =
            trnRepository.findByPortfolioIdAndCashAssetId(
                portfolio.id,
                usdCashAsset.id,
                LocalDate.now(),
                TrnStatus.SETTLED
            )

        // Both transactions should be in the cash ladder
        // - The deposit has cashAsset = usdCashAsset
        // - The buy has cashAsset = usdCashAsset (debit to cash)
        assertThat(cashLadderTrns).hasSize(2)

        // Verify transactions are sorted by trade date descending
        val trnList = cashLadderTrns.toList()
        assertThat(trnList).allMatch { it.cashAsset?.id == usdCashAsset.id }
    }

    @Test
    fun `cash ladder does not include transactions with different cash asset`() {
        // Create two cash assets
        val usdCashInput = AssetUtils.getCash(USD.code)
        val usdCashAsset =
            assetService
                .handle(
                    AssetRequest(mapOf(Pair("USD2", usdCashInput)))
                ).data["USD2"]
        assertThat(usdCashAsset).isNotNull

        val otherCashInput = AssetUtils.getCash("EUR")
        val eurCashAsset =
            assetService
                .handle(
                    AssetRequest(mapOf(Pair("EUR", otherCashInput)))
                ).data["EUR"]
        assertThat(eurCashAsset).isNotNull

        // Create equity
        val equity =
            assetService
                .handle(
                    AssetRequest(
                        AssetInput(NYSE.code, "AAPL"),
                        "AAPL"
                    )
                ).data["AAPL"]
        assertThat(equity).isNotNull

        // Create portfolio
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "cashLadderTest2",
                    currency = USD.code
                )
            )

        // Create transactions with different cash assets
        val usdBuy =
            TrnInput(
                callerRef = CallerRef(),
                assetId = equity!!.id,
                cashAssetId = usdCashAsset!!.id,
                cashCurrency = USD.code,
                trnType = TrnType.BUY,
                quantity = BigDecimal("50"),
                price = BigDecimal("200.00"),
                tradeAmount = BigDecimal("10000.00"),
                status = TrnStatus.SETTLED
            )

        val eurBuy =
            TrnInput(
                callerRef = CallerRef(),
                assetId = equity.id,
                cashAssetId = eurCashAsset!!.id,
                cashCurrency = "EUR",
                trnType = TrnType.BUY,
                quantity = BigDecimal("25"),
                price = BigDecimal("200.00"),
                tradeAmount = BigDecimal("5000.00"),
                status = TrnStatus.SETTLED
            )

        trnService.save(
            portfolio,
            TrnRequest(portfolio.id, listOf(usdBuy, eurBuy))
        )

        // Query cash ladder for USD cash only
        val usdCashLadder =
            trnRepository.findByPortfolioIdAndCashAssetId(
                portfolio.id,
                usdCashAsset.id,
                LocalDate.now(),
                TrnStatus.SETTLED
            )

        // Should only have 1 transaction (the USD buy)
        assertThat(usdCashLadder).hasSize(1)
        assertThat(usdCashLadder.first().cashAsset?.id).isEqualTo(usdCashAsset.id)
    }

    @Test
    fun `cash ladder includes FX_BUY transactions where asset is the purchased currency`() {
        // Create USD and SGD cash assets
        val usdCashInput = AssetUtils.getCash(USD.code)
        val usdCashAsset =
            assetService
                .handle(
                    AssetRequest(mapOf(Pair("USD-FX", usdCashInput)))
                ).data["USD-FX"]
        assertThat(usdCashAsset).isNotNull

        val sgdCashInput = AssetUtils.getCash("SGD")
        val sgdCashAsset =
            assetService
                .handle(
                    AssetRequest(mapOf(Pair("SGD", sgdCashInput)))
                ).data["SGD"]
        assertThat(sgdCashAsset).isNotNull

        // Create portfolio
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "cashLadderFxTest",
                    currency = USD.code
                )
            )

        // Create FX_BUY transaction: Buy USD with SGD
        // asset = USD (what we're buying)
        // cashAsset = SGD (what we're spending)
        val fxBuyUsd =
            TrnInput(
                callerRef = CallerRef(),
                assetId = usdCashAsset!!.id,
                cashAssetId = sgdCashAsset!!.id,
                cashCurrency = "SGD",
                trnType = TrnType.FX_BUY,
                quantity = BigDecimal("10000.00"), // USD amount purchased
                price = BigDecimal.ONE,
                tradeAmount = BigDecimal("10000.00"),
                cashAmount = BigDecimal("-13500.00"), // SGD amount spent (negative)
                status = TrnStatus.SETTLED
            )

        // Create a USD deposit for comparison
        val depositUsd =
            TrnInput(
                callerRef = CallerRef(),
                assetId = usdCashAsset.id,
                cashAssetId = usdCashAsset.id,
                cashCurrency = USD.code,
                trnType = TrnType.DEPOSIT,
                quantity = BigDecimal("5000.00"),
                tradeAmount = BigDecimal("5000.00"),
                price = BigDecimal.ONE,
                status = TrnStatus.SETTLED
            )

        trnService.save(
            portfolio,
            TrnRequest(portfolio.id, listOf(fxBuyUsd, depositUsd))
        )

        // Query cash ladder for USD - should include BOTH the deposit AND the FX_BUY
        val usdCashLadder =
            trnRepository.findByPortfolioIdAndCashAssetId(
                portfolio.id,
                usdCashAsset.id,
                LocalDate.now(),
                TrnStatus.SETTLED
            )

        // Should have 2 transactions:
        // - The DEPOSIT (cashAsset = USD)
        // - The FX_BUY (asset = USD, purchased currency)
        assertThat(usdCashLadder).hasSize(2)

        val trnTypes = usdCashLadder.map { it.trnType }
        assertThat(trnTypes).contains(TrnType.DEPOSIT)
        assertThat(trnTypes).contains(TrnType.FX_BUY)

        // Query cash ladder for SGD - should only include the FX_BUY (as a debit)
        val sgdCashLadder =
            trnRepository.findByPortfolioIdAndCashAssetId(
                portfolio.id,
                sgdCashAsset.id,
                LocalDate.now(),
                TrnStatus.SETTLED
            )

        // Should have 1 transaction: the FX_BUY (cashAsset = SGD)
        assertThat(sgdCashLadder).hasSize(1)
        assertThat(sgdCashLadder.first().trnType).isEqualTo(TrnType.FX_BUY)
        assertThat(sgdCashLadder.first().cashAsset?.id).isEqualTo(sgdCashAsset.id)
    }
}