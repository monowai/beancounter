package com.beancounter.marketdata.offmarket

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
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
import com.beancounter.marketdata.assets.OwnedAssetService
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.cash.CashService
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.TrnService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    private lateinit var ownedAssetService: OwnedAssetService

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

    @Autowired
    private lateinit var defaultEnricher: DefaultEnricher

    @BeforeEach
    fun configure() {
        enrichmentFactory.register(defaultEnricher)
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
            .hasFieldOrPropertyWithValue("accountingType.currency.code", USD.code)
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
            .hasFieldOrPropertyWithValue("accountingType.currency.code", NZD.code)
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
        val myAccounts = ownedAssetService.findByOwnerAndCategory(AssetCategory.ACCOUNT)
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
        ownedAssetService.deleteOwnedAsset(createdAsset.id)

        // Verify asset is deleted
        assertThat(assetFinder.findLocally(deleteTestAccount)).isNull()

        // Verify transaction is also deleted
        assertThat(trnRepository.findById(savedTrn.id)).isEmpty
    }

    @Test
    fun isFindByOwnerReturningAllCategories() {
        val sysUser = SystemUser(id = "find-owner-user")
        mockAuthConfig.login(sysUser, this.systemUserService)

        // Create assets in different categories
        val account =
            AssetInput.toAccount(
                currency = USD,
                code = "OWNER-SAVINGS",
                name = "Owner Savings",
                owner = sysUser.id
            )
        val reAsset =
            AssetInput.toRealEstate(
                currency = NZD,
                code = "OWNER-HOUSE",
                name = "Owner House",
                owner = sysUser.id
            )
        assetService.handle(
            AssetRequest(
                mapOf("savings" to account, "house" to reAsset)
            )
        )

        // findByOwner returns all categories
        val allAssets = ownedAssetService.findByOwner()
        assertThat(allAssets.data)
            .containsKey("OWNER-SAVINGS")
            .containsKey("OWNER-HOUSE")

        // findByOwnerAndCategory filters correctly
        val accountsOnly = ownedAssetService.findByOwnerAndCategory(AssetCategory.ACCOUNT)
        assertThat(accountsOnly.data)
            .containsKey("OWNER-SAVINGS")
            .doesNotContainKey("OWNER-HOUSE")

        val reOnly = ownedAssetService.findByOwnerAndCategory(AssetCategory.RE)
        assertThat(reOnly.data)
            .containsKey("OWNER-HOUSE")
            .doesNotContainKey("OWNER-SAVINGS")
    }

    @Test
    fun isUpdateOwnedAssetChangingFields() {
        val sysUser = SystemUser(id = "update-test-user")
        mockAuthConfig.login(sysUser, this.systemUserService)

        val original =
            AssetInput.toAccount(
                currency = USD,
                code = "UPDATE-ACCT",
                name = "Original Name",
                owner = sysUser.id
            )
        val response = assetService.handle(AssetRequest(mapOf("update" to original)))
        val createdAsset = response.data["update"]!!

        // Update name and currency
        val updateInput =
            AssetInput(
                market = "PRIVATE",
                code = "UPDATE-ACCT",
                name = "Updated Name",
                currency = NZD.code,
                category = AssetCategory.ACCOUNT,
                owner = sysUser.id
            )
        val updated = ownedAssetService.updateOwnedAsset(createdAsset.id, updateInput)
        assertThat(updated)
            .hasFieldOrPropertyWithValue("name", "Updated Name")
            .hasFieldOrPropertyWithValue("accountingType.currency.code", NZD.code)
    }

    @Test
    fun isDeleteOwnedAssetRejectedForWrongUser() {
        val owner = SystemUser(id = "asset-owner")
        mockAuthConfig.login(owner, this.systemUserService)

        val account =
            AssetInput.toAccount(
                currency = USD,
                code = "WRONG-USER-DELETE",
                name = "Owner's Asset",
                owner = owner.id
            )
        val response = assetService.handle(AssetRequest(mapOf("test" to account)))
        val asset = response.data["test"]!!

        // Switch to different user
        val otherUser = SystemUser(id = "other-user")
        mockAuthConfig.login(otherUser, this.systemUserService)

        assertThatThrownBy { ownedAssetService.deleteOwnedAsset(asset.id) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("not owned")
    }

    @Test
    fun isUpdateOwnedAssetRejectedForWrongUser() {
        val owner = SystemUser(id = "update-owner")
        mockAuthConfig.login(owner, this.systemUserService)

        val account =
            AssetInput.toAccount(
                currency = USD,
                code = "WRONG-USER-UPDATE",
                name = "Owner's Asset",
                owner = owner.id
            )
        val response = assetService.handle(AssetRequest(mapOf("test" to account)))
        val asset = response.data["test"]!!

        // Switch to different user
        val otherUser = SystemUser(id = "update-other-user")
        mockAuthConfig.login(otherUser, this.systemUserService)

        val updateInput =
            AssetInput(
                market = "PRIVATE",
                code = "WRONG-USER-UPDATE",
                name = "Hacked Name",
                category = AssetCategory.ACCOUNT
            )
        assertThatThrownBy { ownedAssetService.updateOwnedAsset(asset.id, updateInput) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("not owned")
    }

    @Test
    fun isDeleteNonExistentAssetThrowingNotFound() {
        val sysUser = SystemUser(id = "not-found-user")
        mockAuthConfig.login(sysUser, this.systemUserService)

        assertThatThrownBy { ownedAssetService.deleteOwnedAsset("non-existent-id") }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun isGetCurrentOwnerIdReturningUserId() {
        val sysUser = SystemUser(id = "owner-id-user")
        mockAuthConfig.login(sysUser, this.systemUserService)

        assertThat(ownedAssetService.getCurrentOwnerId()).isEqualTo(sysUser.id)
    }
}