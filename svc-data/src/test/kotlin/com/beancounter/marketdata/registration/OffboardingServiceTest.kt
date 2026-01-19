package com.beancounter.marketdata.registration

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.tax.TaxRateRequest
import com.beancounter.marketdata.tax.TaxRateService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

/**
 * Tests for user offboarding functionality.
 */
@SpringMvcDbTest
class OffboardingServiceTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var offboardingService: OffboardingService

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var taxRateService: TaxRateService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var systemUserService: Registration

    @Test
    fun `should return empty summary for user with no data`() {
        val user = SystemUser(id = "offboard-empty-user", email = "offboard-empty@test.com")
        mockAuthConfig.login(user, systemUserService)

        val summary = offboardingService.getSummary()

        assertThat(summary.portfolioCount).isEqualTo(0)
        assertThat(summary.assetCount).isEqualTo(0)
        assertThat(summary.taxRateCount).isEqualTo(0)
    }

    @Test
    fun `should return correct summary with user data`() {
        val user = SystemUser(id = "offboard-summary-user", email = "offboard-summary@test.com")
        mockAuthConfig.login(user, systemUserService)

        // Create a portfolio
        portfolioService.save(
            listOf(
                PortfolioInput(
                    code = "OFFBOARD-P1",
                    name = "Offboard Portfolio 1",
                    currency = USD.code
                )
            )
        )

        // Create a custom asset
        assetService.handle(
            AssetRequest(
                mapOf(
                    "test" to
                        AssetInput.toRealEstate(
                            currency = NZD,
                            code = "OFFBOARD-HOUSE",
                            name = "Test House",
                            owner = user.id
                        )
                )
            )
        )

        // Create a tax rate
        taxRateService.saveTaxRate(
            TaxRateRequest(
                countryCode = "NZ",
                rate = BigDecimal("0.33")
            )
        )

        val summary = offboardingService.getSummary()

        assertThat(summary.portfolioCount).isEqualTo(1)
        assertThat(summary.assetCount).isEqualTo(1)
        assertThat(summary.taxRateCount).isEqualTo(1)
    }

    @Test
    fun `should delete user assets`() {
        val user = SystemUser(id = "offboard-asset-user", email = "offboard-asset@test.com")
        mockAuthConfig.login(user, systemUserService)

        // Create a custom asset
        assetService.handle(
            AssetRequest(
                mapOf(
                    "test" to
                        AssetInput.toRealEstate(
                            currency = NZD,
                            code = "OFFBOARD-ASSET",
                            name = "Test Asset",
                            owner = user.id
                        )
                )
            )
        )

        // Verify asset exists
        var summary = offboardingService.getSummary()
        assertThat(summary.assetCount).isEqualTo(1)

        // Delete assets
        val result = offboardingService.deleteUserAssets()

        assertThat(result.success).isTrue()
        assertThat(result.deletedCount).isEqualTo(1)
        assertThat(result.type).isEqualTo("assets")

        // Verify asset is deleted
        summary = offboardingService.getSummary()
        assertThat(summary.assetCount).isEqualTo(0)
    }

    @Test
    fun `should delete user portfolios`() {
        val user = SystemUser(id = "offboard-portfolio-user", email = "offboard-portfolio@test.com")
        mockAuthConfig.login(user, systemUserService)

        // Create portfolios
        portfolioService.save(
            listOf(
                PortfolioInput(
                    code = "OFFBOARD-P2",
                    name = "Offboard Portfolio 2",
                    currency = USD.code
                ),
                PortfolioInput(
                    code = "OFFBOARD-P3",
                    name = "Offboard Portfolio 3",
                    currency = NZD.code
                )
            )
        )

        // Verify portfolios exist
        var summary = offboardingService.getSummary()
        assertThat(summary.portfolioCount).isEqualTo(2)

        // Delete portfolios
        val result = offboardingService.deleteUserPortfolios()

        assertThat(result.success).isTrue()
        assertThat(result.deletedCount).isEqualTo(2)
        assertThat(result.type).isEqualTo("portfolios")

        // Verify portfolios are deleted
        summary = offboardingService.getSummary()
        assertThat(summary.portfolioCount).isEqualTo(0)
    }

    @Test
    fun `should delete entire user account`() {
        val user = SystemUser(id = "offboard-account-user", email = "offboard-account@test.com")
        mockAuthConfig.login(user, systemUserService)

        // Create some data
        portfolioService.save(
            listOf(
                PortfolioInput(
                    code = "OFFBOARD-ACCT",
                    name = "Account Portfolio",
                    currency = USD.code
                )
            )
        )

        assetService.handle(
            AssetRequest(
                mapOf(
                    "test" to
                        AssetInput.toRealEstate(
                            currency = NZD,
                            code = "OFFBOARD-ACCT-ASSET",
                            name = "Account Asset",
                            owner = user.id
                        )
                )
            )
        )

        taxRateService.saveTaxRate(
            TaxRateRequest(
                countryCode = "AU",
                rate = BigDecimal("0.30")
            )
        )

        // Delete account
        val result = offboardingService.deleteUserAccount()

        assertThat(result.success).isTrue()
        assertThat(result.type).isEqualTo("account")
        assertThat(result.message).isEqualTo("Account and all data deleted")
    }

    @Test
    fun `should handle deleting assets when none exist`() {
        val user = SystemUser(id = "offboard-no-assets-user", email = "offboard-no-assets@test.com")
        mockAuthConfig.login(user, systemUserService)

        val result = offboardingService.deleteUserAssets()

        assertThat(result.success).isTrue()
        assertThat(result.deletedCount).isEqualTo(0)
        assertThat(result.message).isEqualTo("No assets to delete")
    }

    @Test
    fun `should handle deleting portfolios when none exist`() {
        val user = SystemUser(id = "offboard-no-portfolios-user", email = "offboard-no-portfolios@test.com")
        mockAuthConfig.login(user, systemUserService)

        val result = offboardingService.deleteUserPortfolios()

        assertThat(result.success).isTrue()
        assertThat(result.deletedCount).isEqualTo(0)
        assertThat(result.message).isEqualTo("No portfolios to delete")
    }
}