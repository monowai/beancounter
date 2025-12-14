package com.beancounter.marketdata.offmarket

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetCategoryConfig
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.cash.CashService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Verify the flow of creating user-scoped bank account assets.
 */
@SpringMvcDbTest
class BankAccountAssetTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var cashService: CashService

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var assetCategoryConfig: AssetCategoryConfig

    @Autowired
    private lateinit var systemUserService: Registration

    @Autowired
    private lateinit var assetFinder: AssetFinder

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
}