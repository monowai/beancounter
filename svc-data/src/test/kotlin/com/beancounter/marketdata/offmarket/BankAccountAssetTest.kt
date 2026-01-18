package com.beancounter.marketdata.offmarket

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetCategoryConfig
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.cash.CashService
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.TrnService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

/**
 * Verify the flow of creating user-scoped bank account assets.
 */
@SpringMvcDbTest
class BankAccountAssetTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var cashService: CashService

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @MockitoBean
    private lateinit var fxClientService: FxRateService

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var trnService: TrnService

    @Autowired
    private lateinit var trnRepository: TrnRepository

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var assetCategoryConfig: AssetCategoryConfig

    @Autowired
    private lateinit var systemUserService: Registration

    @Autowired
    private lateinit var assetFinder: AssetFinder

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @BeforeEach
    fun configure() {
        enrichmentFactory.register(DefaultEnricher())
        `when`(fxClientService.getRates(any(), any()))
            .thenReturn(FxResponse(FxPairResults()))
    }

    private val savingsAccount =
        AssetInput.toAccount(
            currency = USD,
            code = "USD-SAVINGS",
            name = "My USD Savings Account",
            owner = "test-user"
        )

    private val mortgageAccount =
        AssetInput.toAccount(
            currency = NZD,
            code = "KB-MORTGAGE",
            name = "Kiwibank Mortgage",
            owner = "test-user"
        )

    @Test
    fun isAccountAssetCreated() {
        val sysUser = SystemUser(id = "test-user")
        val token =
            mockAuthConfig.login(
                sysUser,
                this.systemUserService
            )
        assertThat(token)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "subject",
                sysUser.id
            )

        val category = assetCategoryConfig.get(AssetCategory.ACCOUNT)
        assertThat(category)
            .isNotNull
            .hasFieldOrPropertyWithValue("id", "ACCOUNT")
            .hasFieldOrPropertyWithValue("name", "Bank Account")

        // Create savings account
        val savingsResponse =
            assetService.handle(
                AssetRequest(
                    mapOf(Pair("savings", savingsAccount))
                )
            )
        assertThat(savingsResponse.data).hasSize(1)
        val savingsAsset = savingsResponse.data["savings"]
        assertThat(savingsAsset)
            .isNotNull
            .hasFieldOrPropertyWithValue("assetCategory", category)
            .hasFieldOrPropertyWithValue("name", "My USD Savings Account")
            .hasFieldOrPropertyWithValue("priceSymbol", USD.code)
            .hasFieldOrPropertyWithValue("systemUser", sysUser)

        // Create mortgage account
        val mortgageResponse =
            assetService.handle(
                AssetRequest(
                    mapOf(Pair("mortgage", mortgageAccount))
                )
            )
        assertThat(mortgageResponse.data).hasSize(1)
        val mortgageAsset = mortgageResponse.data["mortgage"]
        assertThat(mortgageAsset)
            .isNotNull
            .hasFieldOrPropertyWithValue("assetCategory", category)
            .hasFieldOrPropertyWithValue("name", "Kiwibank Mortgage")
            .hasFieldOrPropertyWithValue("priceSymbol", NZD.code)
            .hasFieldOrPropertyWithValue("systemUser", sysUser)

        // Verify idempotent creation - same user, same code returns same asset
        assertThat(assetService.findOrCreate(savingsAccount))
            .isEqualTo(savingsAsset)

        // Verify findLocally works
        assertThat(assetFinder.findLocally(savingsAccount))
            .isEqualTo(savingsAsset)
        assertThat(assetFinder.findLocally(mortgageAccount))
            .isEqualTo(mortgageAsset)

        // Verify findByOwnerAndCategory returns user's accounts
        val myAccounts = assetService.findByOwnerAndCategory(AssetCategory.ACCOUNT)
        assertThat(myAccounts.data)
            .hasSize(2)
            .containsKey("USD-SAVINGS")
            .containsKey("KB-MORTGAGE")
    }

    @Test
    fun isDeletingAssetCascadesToTransactions() {
        val sysUser = SystemUser(id = "cascade-test-user")
        mockAuthConfig.login(sysUser, this.systemUserService)

        // Create a bank account asset
        val deleteTestAccount =
            AssetInput.toAccount(
                currency = USD,
                code = "DELETE-TEST-ACCOUNT",
                name = "Account to Delete",
                owner = sysUser.id
            )
        val assetResponse =
            assetService.handle(
                AssetRequest(mapOf(Pair("delete-test", deleteTestAccount)))
            )
        val createdAsset = assetResponse.data["delete-test"]
        assertThat(createdAsset).isNotNull

        // Create a portfolio using the service directly
        val portfolios = portfolioService.save(listOf(PortfolioInput("CASCADE-DELETE-TEST")))
        assertThat(portfolios).hasSize(1)
        val portfolio = portfolios.first()

        // Create a transaction for the asset
        val deposit =
            TrnInput(
                callerRef = CallerRef(),
                assetId = createdAsset!!.id,
                trnType = TrnType.DEPOSIT,
                tradeAmount = BigDecimal("1000.00"),
                tradeCashRate = BigDecimal.ONE
            )
        val savedTrns =
            trnService.save(
                portfolio,
                TrnRequest(portfolio.id, listOf(deposit))
            )
        assertThat(savedTrns).hasSize(1)
        val savedTrn = savedTrns.first()

        // Verify transaction exists
        assertThat(trnRepository.findById(savedTrn.id)).isPresent

        // Delete the asset - should cascade delete the transaction
        assetService.deleteOwnedAsset(createdAsset.id)

        // Verify asset is deleted
        assertThat(assetFinder.findLocally(deleteTestAccount)).isNull()

        // Verify transaction is also deleted
        assertThat(trnRepository.findById(savedTrn.id)).isEmpty
    }
}