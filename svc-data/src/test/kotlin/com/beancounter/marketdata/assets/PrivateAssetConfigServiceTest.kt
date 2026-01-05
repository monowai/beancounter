package com.beancounter.marketdata.assets

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional

class PrivateAssetConfigServiceTest {
    private lateinit var configRepository: PrivateAssetConfigRepository
    private lateinit var assetRepository: AssetRepository
    private lateinit var systemUserService: SystemUserService
    private lateinit var configService: PrivateAssetConfigService

    private val userId = "user-123"
    private val assetId = "asset-456"
    private val user = SystemUser(id = userId, email = "test@test.com")
    private val privateMarket = Market(code = "PRIVATE")

    @BeforeEach
    fun setUp() {
        configRepository = mock()
        assetRepository = mock()
        systemUserService = mock()
        configService =
            PrivateAssetConfigService(
                configRepository,
                assetRepository,
                systemUserService
            )
    }

    private fun createTestAsset(ownerId: String = userId): Asset =
        Asset(
            id = assetId,
            code = "MY-PROPERTY",
            name = "Test Property",
            market = privateMarket,
            category = "RE",
            systemUser = SystemUser(id = ownerId, email = "test@test.com")
        )

    @Test
    fun `getMyConfigs returns configs for current user`() {
        val configs =
            listOf(
                PrivateAssetConfig(
                    assetId = "asset-1",
                    monthlyRentalIncome = BigDecimal("1500"),
                    rentalCurrency = "NZD"
                ),
                PrivateAssetConfig(
                    assetId = "asset-2",
                    monthlyRentalIncome = BigDecimal("2000"),
                    rentalCurrency = "SGD"
                )
            )

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(configRepository.findByUserId(userId)).thenReturn(configs)

        val response = configService.getMyConfigs()

        assertThat(response.data).hasSize(2)
    }

    @Test
    fun `getMyConfigs returns empty when no user`() {
        whenever(systemUserService.getActiveUser()).thenReturn(null)

        val response = configService.getMyConfigs()

        assertThat(response.data).isEmpty()
    }

    @Test
    fun `saveConfig creates new config`() {
        val asset = createTestAsset()
        val request =
            PrivateAssetConfigRequest(
                monthlyRentalIncome = BigDecimal("2500"),
                rentalCurrency = "SGD",
                monthlyManagementFee = BigDecimal("200"),
                liquidationPriority = 1,
                transactionDayOfMonth = 15,
                creditAccountId = "bank-123"
            )

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(asset))
        whenever(configRepository.findById(assetId)).thenReturn(Optional.empty())
        whenever(configRepository.save(any<PrivateAssetConfig>())).thenAnswer { it.arguments[0] }

        val response = configService.saveConfig(assetId, request)

        assertThat(response.data.assetId).isEqualTo(assetId)
        assertThat(response.data.monthlyRentalIncome).isEqualByComparingTo(BigDecimal("2500"))
        assertThat(response.data.rentalCurrency).isEqualTo("SGD")
        assertThat(response.data.monthlyManagementFee).isEqualByComparingTo(BigDecimal("200"))
        assertThat(response.data.liquidationPriority).isEqualTo(1)
        assertThat(response.data.transactionDayOfMonth).isEqualTo(15)
        assertThat(response.data.creditAccountId).isEqualTo("bank-123")
    }

    @Test
    fun `saveConfig updates existing config`() {
        val asset = createTestAsset()
        val existing =
            PrivateAssetConfig(
                assetId = assetId,
                monthlyRentalIncome = BigDecimal("1500"),
                rentalCurrency = "NZD",
                liquidationPriority = 2
            )
        val request =
            PrivateAssetConfigRequest(
                monthlyRentalIncome = BigDecimal("2000"),
                liquidationPriority = 1
            )

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(asset))
        whenever(configRepository.findById(assetId)).thenReturn(Optional.of(existing))
        whenever(configRepository.save(any<PrivateAssetConfig>())).thenAnswer { it.arguments[0] }

        val response = configService.saveConfig(assetId, request)

        assertThat(response.data.monthlyRentalIncome).isEqualByComparingTo(BigDecimal("2000"))
        assertThat(response.data.rentalCurrency).isEqualTo("NZD") // Unchanged
        assertThat(response.data.liquidationPriority).isEqualTo(1)
    }

    @Test
    fun `saveConfig sets rental to zero for primary residence`() {
        val asset = createTestAsset()
        val request =
            PrivateAssetConfigRequest(
                monthlyRentalIncome = BigDecimal("3000"), // Should be ignored
                isPrimaryResidence = true,
                liquidationPriority = 100
            )

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(asset))
        whenever(configRepository.findById(assetId)).thenReturn(Optional.empty())
        whenever(configRepository.save(any<PrivateAssetConfig>())).thenAnswer { it.arguments[0] }

        val response = configService.saveConfig(assetId, request)

        assertThat(response.data.isPrimaryResidence).isTrue()
        assertThat(response.data.monthlyRentalIncome).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `saveConfig throws when asset not owned by user`() {
        val asset = createTestAsset(ownerId = "other-user")

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(asset))

        assertThatThrownBy {
            configService.saveConfig(
                assetId,
                PrivateAssetConfigRequest()
            )
        }.isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("not owned by current user")
    }

    @Test
    fun `saveConfig throws when asset not found`() {
        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(assetRepository.findById(assetId)).thenReturn(Optional.empty())

        assertThatThrownBy {
            configService.saveConfig(
                assetId,
                PrivateAssetConfigRequest()
            )
        }.isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining("Asset not found")
    }

    @Test
    fun `deleteConfig removes config`() {
        val asset = createTestAsset()

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(asset))
        whenever(configRepository.existsById(assetId)).thenReturn(true)

        configService.deleteConfig(assetId)

        verify(configRepository).deleteById(assetId)
    }

    @Test
    fun `deleteConfig throws when config not found`() {
        val asset = createTestAsset()

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(asset))
        whenever(configRepository.existsById(assetId)).thenReturn(false)

        assertThatThrownBy { configService.deleteConfig(assetId) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining("Config not found")
    }

    @Test
    fun `getNetMonthlyIncome calculates correctly with fixed fee`() {
        val config =
            PrivateAssetConfig(
                assetId = assetId,
                monthlyRentalIncome = BigDecimal("2000"),
                monthlyManagementFee = BigDecimal("300"),
                managementFeePercent = BigDecimal.ZERO
            )

        val netIncome = config.getNetMonthlyIncome()

        assertThat(netIncome).isEqualByComparingTo(BigDecimal("1700"))
    }

    @Test
    fun `getNetMonthlyIncome uses percentage when greater than fixed fee`() {
        val config =
            PrivateAssetConfig(
                assetId = assetId,
                monthlyRentalIncome = BigDecimal("2000"),
                monthlyManagementFee = BigDecimal("100"), // Fixed: $100
                managementFeePercent = BigDecimal("0.10") // 10%: $200
            )

        val netIncome = config.getNetMonthlyIncome()

        // Should use 10% = $200, not $100 fixed
        assertThat(netIncome).isEqualByComparingTo(BigDecimal("1800"))
    }

    @Test
    fun `getNetMonthlyIncome deducts all expense types`() {
        val config =
            PrivateAssetConfig(
                assetId = assetId,
                monthlyRentalIncome = BigDecimal("3000"),
                monthlyManagementFee = BigDecimal("300"), // $300/mo
                managementFeePercent = BigDecimal.ZERO,
                monthlyBodyCorporateFee = BigDecimal("400"), // $400/mo
                annualPropertyTax = BigDecimal("2400"), // $200/mo
                annualInsurance = BigDecimal("1200"), // $100/mo
                monthlyOtherExpenses = BigDecimal("50") // $50/mo
            )

        val netIncome = config.getNetMonthlyIncome()

        // $3000 - $300 - $400 - $200 - $100 - $50 = $1950
        assertThat(netIncome).isEqualByComparingTo(BigDecimal("1950"))
    }

    @Test
    fun `getTotalMonthlyExpenses calculates all expenses`() {
        val config =
            PrivateAssetConfig(
                assetId = assetId,
                monthlyRentalIncome = BigDecimal("3000"),
                monthlyManagementFee = BigDecimal("300"),
                managementFeePercent = BigDecimal.ZERO,
                monthlyBodyCorporateFee = BigDecimal("400"),
                annualPropertyTax = BigDecimal("2400"), // $200/mo
                annualInsurance = BigDecimal("1200"), // $100/mo
                monthlyOtherExpenses = BigDecimal("50")
            )

        val totalExpenses = config.getTotalMonthlyExpenses()

        // $300 + $400 + $200 + $100 + $50 = $1050
        assertThat(totalExpenses).isEqualByComparingTo(BigDecimal("1050"))
    }

    @Test
    fun `getConfigsForAssets returns configs for multiple assets`() {
        val assetIds = listOf("asset-1", "asset-2", "asset-3")
        val configs =
            listOf(
                PrivateAssetConfig(assetId = "asset-1"),
                PrivateAssetConfig(assetId = "asset-2")
            )

        whenever(configRepository.findByAssetIdIn(assetIds)).thenReturn(configs)

        val response = configService.getConfigsForAssets(assetIds)

        assertThat(response.data).hasSize(2)
    }
}