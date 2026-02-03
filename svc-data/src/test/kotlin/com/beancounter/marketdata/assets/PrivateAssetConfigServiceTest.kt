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
import java.time.LocalDate
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

    @Test
    fun `getTaxableIncome returns rent minus expenses`() {
        val config =
            PrivateAssetConfig(
                assetId = assetId,
                monthlyRentalIncome = BigDecimal("1000"),
                monthlyManagementFee = BigDecimal("100")
            )

        val taxableIncome = config.getTaxableIncome()

        // $1000 - $100 = $900
        assertThat(taxableIncome).isEqualByComparingTo(BigDecimal("900"))
    }

    @Test
    fun `getTaxableIncome returns zero when expenses exceed rent`() {
        val config =
            PrivateAssetConfig(
                assetId = assetId,
                monthlyRentalIncome = BigDecimal("500"),
                monthlyManagementFee = BigDecimal("600")
            )

        val taxableIncome = config.getTaxableIncome()

        // Cannot be negative - max'd at zero
        assertThat(taxableIncome).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `getMonthlyIncomeTax deducts tax when deductIncomeTax is true`() {
        val config =
            PrivateAssetConfig(
                assetId = assetId,
                monthlyRentalIncome = BigDecimal("1000"),
                monthlyManagementFee = BigDecimal("100"),
                deductIncomeTax = true
            )
        val taxRate = BigDecimal("0.20") // 20%

        val tax = config.getMonthlyIncomeTax(taxRate)

        // Taxable = $1000 - $100 = $900, Tax = $900 × 0.20 = $180
        assertThat(tax).isEqualByComparingTo(BigDecimal("180"))
    }

    @Test
    fun `getMonthlyIncomeTax returns zero when deductIncomeTax is false`() {
        val config =
            PrivateAssetConfig(
                assetId = assetId,
                monthlyRentalIncome = BigDecimal("1000"),
                monthlyManagementFee = BigDecimal("100"),
                deductIncomeTax = false
            )
        val taxRate = BigDecimal("0.20") // 20%

        val tax = config.getMonthlyIncomeTax(taxRate)

        // Flag is false, so no tax deducted
        assertThat(tax).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `getNetMonthlyIncome deducts tax when flag is true`() {
        val config =
            PrivateAssetConfig(
                assetId = assetId,
                monthlyRentalIncome = BigDecimal("1000"),
                monthlyManagementFee = BigDecimal("100"),
                deductIncomeTax = true
            )
        val taxRate = BigDecimal("0.20") // 20%

        val netIncome = config.getNetMonthlyIncome(taxRate)

        // Taxable = $900, Tax = $180, Net = $720
        assertThat(netIncome).isEqualByComparingTo(BigDecimal("720"))
    }

    @Test
    fun `getNetMonthlyIncome with zero tax rate equals taxable income`() {
        val config =
            PrivateAssetConfig(
                assetId = assetId,
                monthlyRentalIncome = BigDecimal("1000"),
                monthlyManagementFee = BigDecimal("100"),
                deductIncomeTax = true
            )

        val netIncome = config.getNetMonthlyIncome(BigDecimal.ZERO)

        // No tax, so net = taxable = $900
        assertThat(netIncome).isEqualByComparingTo(BigDecimal("900"))
    }

    // ============ Composite Policy Asset Tests ============

    @Test
    fun `saveConfig creates composite config with sub-accounts`() {
        val asset = createTestAsset()
        val request =
            PrivateAssetConfigRequest(
                policyType = PolicyType.CPF,
                lockedUntilDate = LocalDate.of(2040, 1, 1),
                isPension = true,
                subAccounts =
                    listOf(
                        SubAccountRequest(
                            code = "OA",
                            displayName = "Ordinary Account",
                            balance = BigDecimal("50000"),
                            expectedReturnRate = BigDecimal("0.0250"),
                            liquid = true
                        ),
                        SubAccountRequest(
                            code = "SA",
                            displayName = "Special Account",
                            balance = BigDecimal("30000"),
                            expectedReturnRate = BigDecimal("0.0400"),
                            liquid = true
                        ),
                        SubAccountRequest(
                            code = "MA",
                            displayName = "Medisave Account",
                            balance = BigDecimal("20000"),
                            expectedReturnRate = BigDecimal("0.0400"),
                            liquid = false
                        )
                    )
            )

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(asset))
        whenever(configRepository.findById(assetId)).thenReturn(Optional.empty())
        whenever(configRepository.save(any<PrivateAssetConfig>())).thenAnswer { it.arguments[0] }

        val response = configService.saveConfig(assetId, request)

        assertThat(response.data.policyType).isEqualTo(PolicyType.CPF)
        assertThat(response.data.lockedUntilDate).isEqualTo(LocalDate.of(2040, 1, 1))
        assertThat(response.data.isComposite()).isTrue()
        assertThat(response.data.subAccounts).hasSize(3)
        assertThat(response.data.subAccounts.map { it.code }).containsExactly("OA", "SA", "MA")
        // MA is not liquid
        assertThat(
            response.data.subAccounts
                .first { it.code == "MA" }
                .liquid
        ).isFalse()
    }

    @Test
    fun `saveConfig rejects duplicate sub-account codes`() {
        val asset = createTestAsset()
        val request =
            PrivateAssetConfigRequest(
                policyType = PolicyType.CPF,
                subAccounts =
                    listOf(
                        SubAccountRequest(code = "OA", balance = BigDecimal("1000")),
                        SubAccountRequest(code = "oa", balance = BigDecimal("2000"))
                    )
            )

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(asset))

        assertThatThrownBy {
            configService.saveConfig(assetId, request)
        }.isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("Duplicate sub-account codes")
    }

    @Test
    fun `saveConfig updates existing composite config sub-accounts`() {
        val asset = createTestAsset()
        val existing =
            PrivateAssetConfig(
                assetId = assetId,
                policyType = PolicyType.CPF,
                subAccounts =
                    mutableListOf(
                        PrivateAssetSubAccount(
                            assetId = assetId,
                            code = "OA",
                            displayName = "Ordinary Account",
                            balance = BigDecimal("50000"),
                            liquid = true
                        ),
                        PrivateAssetSubAccount(
                            assetId = assetId,
                            code = "SA",
                            displayName = "Special Account",
                            balance = BigDecimal("30000"),
                            liquid = true
                        )
                    )
            )
        // Update: change OA balance, remove SA, add MA
        val request =
            PrivateAssetConfigRequest(
                subAccounts =
                    listOf(
                        SubAccountRequest(
                            code = "OA",
                            displayName = "Ordinary Account",
                            balance = BigDecimal("55000"),
                            liquid = true
                        ),
                        SubAccountRequest(
                            code = "MA",
                            displayName = "Medisave Account",
                            balance = BigDecimal("20000"),
                            liquid = false
                        )
                    )
            )

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(asset))
        whenever(configRepository.findById(assetId)).thenReturn(Optional.of(existing))
        whenever(configRepository.save(any<PrivateAssetConfig>())).thenAnswer { it.arguments[0] }

        val response = configService.saveConfig(assetId, request)

        assertThat(response.data.subAccounts).hasSize(2)
        assertThat(response.data.subAccounts.map { it.code }).containsExactly("OA", "MA")
        assertThat(
            response.data.subAccounts
                .first { it.code == "OA" }
                .balance
        ).isEqualByComparingTo(BigDecimal("55000"))
    }

    @Test
    fun `saveConfig preserves existing fields when no sub-accounts in request`() {
        val asset = createTestAsset()
        val existing =
            PrivateAssetConfig(
                assetId = assetId,
                policyType = PolicyType.CPF,
                lockedUntilDate = LocalDate.of(2040, 1, 1),
                subAccounts =
                    mutableListOf(
                        PrivateAssetSubAccount(
                            assetId = assetId,
                            code = "OA",
                            balance = BigDecimal("50000")
                        )
                    )
            )
        // Update only payoutAge, don't touch sub-accounts
        val request = PrivateAssetConfigRequest(payoutAge = 65)

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(asset))
        whenever(configRepository.findById(assetId)).thenReturn(Optional.of(existing))
        whenever(configRepository.save(any<PrivateAssetConfig>())).thenAnswer { it.arguments[0] }

        val response = configService.saveConfig(assetId, request)

        // Sub-accounts untouched when not in request
        assertThat(response.data.policyType).isEqualTo(PolicyType.CPF)
        assertThat(response.data.lockedUntilDate).isEqualTo(LocalDate.of(2040, 1, 1))
        assertThat(response.data.payoutAge).isEqualTo(65)
        assertThat(response.data.subAccounts).hasSize(1)
    }

    @Test
    fun `isComposite returns false for simple config`() {
        val config = PrivateAssetConfig(assetId = assetId)
        assertThat(config.isComposite()).isFalse()
    }

    @Test
    fun `isComposite returns true when sub-accounts present`() {
        val config =
            PrivateAssetConfig(
                assetId = assetId,
                subAccounts =
                    mutableListOf(
                        PrivateAssetSubAccount(
                            assetId = assetId,
                            code = "OA",
                            balance = BigDecimal("1000")
                        )
                    )
            )
        assertThat(config.isComposite()).isTrue()
    }

    @Test
    fun `existing simple config backward compatibility`() {
        val config =
            PrivateAssetConfig(
                assetId = assetId,
                monthlyRentalIncome = BigDecimal("2000"),
                isPension = true,
                expectedReturnRate = BigDecimal("0.05")
            )

        // Composite fields default to null/empty
        assertThat(config.policyType).isNull()
        assertThat(config.lockedUntilDate).isNull()
        assertThat(config.subAccounts).isEmpty()
        assertThat(config.isComposite()).isFalse()

        // Existing calculations still work
        assertThat(config.getNetMonthlyIncome()).isEqualByComparingTo(BigDecimal("2000"))
    }

    @Test
    fun `sub-account codes normalised to uppercase`() {
        val asset = createTestAsset()
        val request =
            PrivateAssetConfigRequest(
                policyType = PolicyType.GENERIC,
                subAccounts =
                    listOf(
                        SubAccountRequest(
                            code = "fund-a",
                            balance = BigDecimal("10000")
                        )
                    )
            )

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(assetRepository.findById(assetId)).thenReturn(Optional.of(asset))
        whenever(configRepository.findById(assetId)).thenReturn(Optional.empty())
        whenever(configRepository.save(any<PrivateAssetConfig>())).thenAnswer { it.arguments[0] }

        val response = configService.saveConfig(assetId, request)

        assertThat(
            response.data.subAccounts
                .first()
                .code
        ).isEqualTo("FUND-A")
    }
}