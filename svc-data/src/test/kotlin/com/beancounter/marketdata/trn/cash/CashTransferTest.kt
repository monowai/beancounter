package com.beancounter.marketdata.trn.cash

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.cash.CashTransferRequest
import com.beancounter.marketdata.cash.CashTransferService
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.utils.BcMvcHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * TDD tests for cash transfer functionality.
 *
 * A cash transfer moves money between cash assets:
 * - WITHDRAWAL from source asset
 * - DEPOSIT to target asset
 * - Same currency only (for now)
 * - Can be cross-portfolio
 */
@SpringMvcDbTest
class CashTransferTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @MockitoBean
    private lateinit var fxClientService: FxRateService

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    lateinit var assetService: AssetService

    @Autowired
    lateinit var trnService: TrnService

    @Autowired
    lateinit var cashTransferService: CashTransferService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var systemUserService: Registration

    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var bcMvcHelper: BcMvcHelper

    private lateinit var usdCashAsset: Asset
    private lateinit var nzdCashAsset: Asset
    private lateinit var portfolio1: Portfolio
    private lateinit var portfolio2: Portfolio
    private lateinit var testUser: SystemUser

    private val tenK = BigDecimal("10000.00")

    @BeforeEach
    fun configure() {
        // Set up authentication context for direct service calls - store user for re-login
        testUser = SystemUser()
        val token = mockAuthConfig.login(testUser, systemUserService)
        bcMvcHelper = BcMvcHelper(mockMvc, token)

        Mockito
            .`when`(
                fxClientService.getRates(
                    any(),
                    any()
                )
            ).thenReturn(FxResponse(FxPairResults()))

        // Create cash assets
        usdCashAsset = getCashBalance(Constants.USD)
        nzdCashAsset = getCashBalance(Constants.NZD)

        // Create portfolios with unique codes
        val uniqueId = UUID.randomUUID().toString().substring(0, 8)
        portfolio1 =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "DBS_$uniqueId",
                    base = "USD",
                    currency = "USD"
                )
            )
        portfolio2 =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "TEST_$uniqueId",
                    base = "USD",
                    currency = "USD"
                )
            )
    }

    @Test
    fun `transfer cash within same portfolio creates withdrawal and deposit transactions`() {
        // Given: A request to transfer $10,000 from USD Balance to another USD Balance within the same portfolio
        // Note: Same asset can be used for both source and destination (e.g., transferring between accounts)
        val request =
            CashTransferRequest(
                fromPortfolioId = portfolio1.id,
                fromAssetId = usdCashAsset.id,
                toPortfolioId = portfolio1.id,
                toAssetId = usdCashAsset.id,
                sentAmount = tenK,
                tradeDate = LocalDate.now()
            )

        // When: The transfer is executed (re-login to restore auth context after MockMvc calls)
        mockAuthConfig.login(testUser, systemUserService)
        val result = cashTransferService.transfer(request)

        // Then: Two transactions are created
        assertThat(result.transactions).hasSize(2)

        // Verify withdrawal
        val withdrawal = result.transactions.find { it.trnType == TrnType.WITHDRAWAL }
        assertThat(withdrawal).isNotNull
        assertThat(withdrawal!!.portfolio.id).isEqualTo(portfolio1.id)
        assertThat(withdrawal.asset.id).isEqualTo(usdCashAsset.id)
        assertThat(withdrawal.tradeAmount).isEqualTo(tenK)
        assertThat(withdrawal.status).isEqualTo(TrnStatus.SETTLED)

        // Verify deposit (same amount when no fee)
        val deposit = result.transactions.find { it.trnType == TrnType.DEPOSIT }
        assertThat(deposit).isNotNull
        assertThat(deposit!!.portfolio.id).isEqualTo(portfolio1.id)
        assertThat(deposit.asset.id).isEqualTo(usdCashAsset.id)
        assertThat(deposit.tradeAmount).isEqualTo(tenK)
        assertThat(deposit.status).isEqualTo(TrnStatus.SETTLED)
    }

    @Test
    fun `transfer cash between different portfolios creates cross-portfolio transactions`() {
        // Given: A request to transfer $10,000 from DBS portfolio to TEST portfolio
        val request =
            CashTransferRequest(
                fromPortfolioId = portfolio1.id,
                fromAssetId = usdCashAsset.id,
                toPortfolioId = portfolio2.id,
                toAssetId = usdCashAsset.id,
                sentAmount = tenK,
                tradeDate = LocalDate.now()
            )

        // When: The transfer is executed (re-login to restore auth context after MockMvc calls)
        mockAuthConfig.login(testUser, systemUserService)
        val result = cashTransferService.transfer(request)

        // Then: Two transactions are created in different portfolios
        assertThat(result.transactions).hasSize(2)

        val withdrawal = result.transactions.find { it.trnType == TrnType.WITHDRAWAL }
        assertThat(withdrawal!!.portfolio.id).isEqualTo(portfolio1.id)

        val deposit = result.transactions.find { it.trnType == TrnType.DEPOSIT }
        assertThat(deposit!!.portfolio.id).isEqualTo(portfolio2.id)
    }

    @Test
    fun `transfer fails when currencies do not match`() {
        // Given: A request to transfer between USD and NZD accounts
        val request =
            CashTransferRequest(
                fromPortfolioId = portfolio1.id,
                fromAssetId = usdCashAsset.id,
                toPortfolioId = portfolio1.id,
                toAssetId = nzdCashAsset.id,
                sentAmount = tenK,
                tradeDate = LocalDate.now()
            )

        // When/Then: The transfer should fail with appropriate error (re-login first)
        mockAuthConfig.login(testUser, systemUserService)
        val exception =
            assertThrows<IllegalArgumentException> {
                cashTransferService.transfer(request)
            }
        assertThat(exception.message).contains("currencies")
    }

    @Test
    fun `transfer fails when sent amount is not positive`() {
        // Given: A request with zero sent amount
        val request =
            CashTransferRequest(
                fromPortfolioId = portfolio1.id,
                fromAssetId = usdCashAsset.id,
                toPortfolioId = portfolio1.id,
                toAssetId = usdCashAsset.id,
                sentAmount = BigDecimal.ZERO,
                tradeDate = LocalDate.now()
            )

        // When/Then: The transfer should fail (re-login first)
        mockAuthConfig.login(testUser, systemUserService)
        val exception =
            assertThrows<IllegalArgumentException> {
                cashTransferService.transfer(request)
            }
        assertThat(exception.message).contains("Sent amount must be positive")
    }

    @Test
    fun `transfer with fee creates different withdrawal and deposit amounts`() {
        // Given: A transfer where $10,020 is sent but only $10,000 is received ($20 fee)
        val sentAmount = BigDecimal("10020.00")
        val receivedAmount = BigDecimal("10000.00")

        val request =
            CashTransferRequest(
                fromPortfolioId = portfolio1.id,
                fromAssetId = usdCashAsset.id,
                toPortfolioId = portfolio2.id,
                toAssetId = usdCashAsset.id,
                sentAmount = sentAmount,
                receivedAmount = receivedAmount,
                tradeDate = LocalDate.now()
            )

        // When: The transfer is executed
        mockAuthConfig.login(testUser, systemUserService)
        val result = cashTransferService.transfer(request)

        // Then: Withdrawal amount reflects what was sent (including fee)
        val withdrawal = result.transactions.find { it.trnType == TrnType.WITHDRAWAL }
        assertThat(withdrawal).isNotNull
        assertThat(withdrawal!!.tradeAmount).isEqualTo(sentAmount)

        // And: Deposit amount reflects what was actually received
        val deposit = result.transactions.find { it.trnType == TrnType.DEPOSIT }
        assertThat(deposit).isNotNull
        assertThat(deposit!!.tradeAmount).isEqualTo(receivedAmount)
    }

    @Test
    fun `transfer fails when received amount exceeds sent amount`() {
        // Given: A request where received > sent (invalid)
        val request =
            CashTransferRequest(
                fromPortfolioId = portfolio1.id,
                fromAssetId = usdCashAsset.id,
                toPortfolioId = portfolio1.id,
                toAssetId = usdCashAsset.id,
                sentAmount = BigDecimal("1000.00"),
                receivedAmount = BigDecimal("1100.00"),
                tradeDate = LocalDate.now()
            )

        // When/Then: The transfer should fail
        mockAuthConfig.login(testUser, systemUserService)
        val exception =
            assertThrows<IllegalArgumentException> {
                cashTransferService.transfer(request)
            }
        assertThat(exception.message).contains("Received amount cannot exceed sent amount")
    }

    private fun getCashBalance(currency: Currency): Asset {
        val cashInput = AssetUtils.getCash(currency.code)
        return assetService
            .handle(
                com.beancounter.common.contracts.AssetRequest(
                    mapOf(
                        Pair(
                            currency.code,
                            cashInput
                        )
                    )
                )
            ).data[currency.code]!!
    }
}