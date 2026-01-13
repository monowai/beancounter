package com.beancounter.marketdata.tax

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
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

class TaxRateServiceTest {
    private lateinit var taxRateRepository: TaxRateRepository
    private lateinit var systemUserService: SystemUserService
    private lateinit var taxRateService: TaxRateService

    private val userId = "user-123"
    private val user = SystemUser(id = userId, email = "test@test.com")

    @BeforeEach
    fun setUp() {
        taxRateRepository = mock()
        systemUserService = mock()
        taxRateService = TaxRateService(taxRateRepository, systemUserService)
    }

    @Test
    fun `getMyTaxRates returns all rates for current user`() {
        val rates =
            listOf(
                TaxRate(owner = user, countryCode = "NZ", rate = BigDecimal("0.20")),
                TaxRate(owner = user, countryCode = "SG", rate = BigDecimal("0.15"))
            )

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(taxRateRepository.findAllByOwnerId(userId)).thenReturn(rates)

        val response = taxRateService.getMyTaxRates()

        assertThat(response.data).hasSize(2)
        assertThat(response.data.map { it.countryCode }).containsExactlyInAnyOrder("NZ", "SG")
    }

    @Test
    fun `getMyTaxRates returns empty when no user`() {
        whenever(systemUserService.getActiveUser()).thenReturn(null)

        val response = taxRateService.getMyTaxRates()

        assertThat(response.data).isEmpty()
    }

    @Test
    fun `getTaxRate returns rate for specific country`() {
        val rate = TaxRate(owner = user, countryCode = "NZ", rate = BigDecimal("0.20"))

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(taxRateRepository.findByOwnerIdAndCountryCode(userId, "NZ"))
            .thenReturn(Optional.of(rate))

        val response = taxRateService.getTaxRate("NZ")

        assertThat(response).isNotNull
        assertThat(response!!.data.countryCode).isEqualTo("NZ")
        assertThat(response.data.rate).isEqualByComparingTo(BigDecimal("0.20"))
    }

    @Test
    fun `getTaxRate returns null when not configured`() {
        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(taxRateRepository.findByOwnerIdAndCountryCode(userId, "AU"))
            .thenReturn(Optional.empty())

        val response = taxRateService.getTaxRate("AU")

        assertThat(response).isNull()
    }

    @Test
    fun `getTaxRate normalizes country code to uppercase`() {
        val rate = TaxRate(owner = user, countryCode = "NZ", rate = BigDecimal("0.20"))

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(taxRateRepository.findByOwnerIdAndCountryCode(userId, "NZ"))
            .thenReturn(Optional.of(rate))

        val response = taxRateService.getTaxRate("nz") // lowercase

        assertThat(response).isNotNull
        assertThat(response!!.data.countryCode).isEqualTo("NZ")
    }

    @Test
    fun `getTaxRateValue returns rate for country`() {
        val rate = TaxRate(owner = user, countryCode = "NZ", rate = BigDecimal("0.20"))

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(taxRateRepository.findByOwnerIdAndCountryCode(userId, "NZ"))
            .thenReturn(Optional.of(rate))

        val result = taxRateService.getTaxRateValue("NZ")

        assertThat(result).isEqualByComparingTo(BigDecimal("0.20"))
    }

    @Test
    fun `getTaxRateValue returns zero when not configured`() {
        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(taxRateRepository.findByOwnerIdAndCountryCode(userId, "AU"))
            .thenReturn(Optional.empty())

        val result = taxRateService.getTaxRateValue("AU")

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `getTaxRateValue returns zero when no user`() {
        whenever(systemUserService.getActiveUser()).thenReturn(null)

        val result = taxRateService.getTaxRateValue("NZ")

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `saveTaxRate creates new rate`() {
        val request = TaxRateRequest(countryCode = "NZ", rate = BigDecimal("0.20"))

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(taxRateRepository.findByOwnerIdAndCountryCode(userId, "NZ"))
            .thenReturn(Optional.empty())
        whenever(taxRateRepository.save(any<TaxRate>())).thenAnswer { it.arguments[0] }

        val response = taxRateService.saveTaxRate(request)

        assertThat(response.data.countryCode).isEqualTo("NZ")
        assertThat(response.data.rate).isEqualByComparingTo(BigDecimal("0.20"))
    }

    @Test
    fun `saveTaxRate updates existing rate`() {
        val existing = TaxRate(owner = user, countryCode = "NZ", rate = BigDecimal("0.15"))
        val request = TaxRateRequest(countryCode = "NZ", rate = BigDecimal("0.20"))

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(taxRateRepository.findByOwnerIdAndCountryCode(userId, "NZ"))
            .thenReturn(Optional.of(existing))
        whenever(taxRateRepository.save(any<TaxRate>())).thenAnswer { it.arguments[0] }

        val response = taxRateService.saveTaxRate(request)

        assertThat(response.data.rate).isEqualByComparingTo(BigDecimal("0.20"))
    }

    @Test
    fun `saveTaxRate normalizes country code to uppercase`() {
        val request = TaxRateRequest(countryCode = "nz", rate = BigDecimal("0.20"))

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(taxRateRepository.findByOwnerIdAndCountryCode(userId, "NZ"))
            .thenReturn(Optional.empty())
        whenever(taxRateRepository.save(any<TaxRate>())).thenAnswer { it.arguments[0] }

        val response = taxRateService.saveTaxRate(request)

        assertThat(response.data.countryCode).isEqualTo("NZ")
    }

    @Test
    fun `saveTaxRate validates country code length`() {
        val request = TaxRateRequest(countryCode = "NZL", rate = BigDecimal("0.20"))

        whenever(systemUserService.getActiveUser()).thenReturn(user)

        assertThatThrownBy { taxRateService.saveTaxRate(request) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("2 letters")
    }

    @Test
    fun `saveTaxRate validates country code contains only letters`() {
        val request = TaxRateRequest(countryCode = "N1", rate = BigDecimal("0.20"))

        whenever(systemUserService.getActiveUser()).thenReturn(user)

        assertThatThrownBy { taxRateService.saveTaxRate(request) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("2 letters")
    }

    @Test
    fun `saveTaxRate validates rate is between 0 and 1`() {
        whenever(systemUserService.getActiveUser()).thenReturn(user)

        // Rate > 1
        assertThatThrownBy {
            taxRateService.saveTaxRate(TaxRateRequest(countryCode = "NZ", rate = BigDecimal("1.5")))
        }.isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("between 0 and 1")

        // Rate < 0
        assertThatThrownBy {
            taxRateService.saveTaxRate(TaxRateRequest(countryCode = "NZ", rate = BigDecimal("-0.1")))
        }.isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("between 0 and 1")
    }

    @Test
    fun `saveTaxRate throws when no user`() {
        whenever(systemUserService.getActiveUser()).thenReturn(null)

        assertThatThrownBy {
            taxRateService.saveTaxRate(TaxRateRequest(countryCode = "NZ", rate = BigDecimal("0.20")))
        }.isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("not authenticated")
    }

    @Test
    fun `deleteTaxRate removes rate`() {
        val existing = TaxRate(owner = user, countryCode = "NZ", rate = BigDecimal("0.20"))

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(taxRateRepository.findByOwnerIdAndCountryCode(userId, "NZ"))
            .thenReturn(Optional.of(existing))

        taxRateService.deleteTaxRate("NZ")

        verify(taxRateRepository).delete(existing)
    }

    @Test
    fun `deleteTaxRate throws when not found`() {
        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(taxRateRepository.findByOwnerIdAndCountryCode(userId, "AU"))
            .thenReturn(Optional.empty())

        assertThatThrownBy { taxRateService.deleteTaxRate("AU") }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining("not found")
    }

    @Test
    fun `deleteTaxRate normalizes country code to uppercase`() {
        val existing = TaxRate(owner = user, countryCode = "NZ", rate = BigDecimal("0.20"))

        whenever(systemUserService.getActiveUser()).thenReturn(user)
        whenever(taxRateRepository.findByOwnerIdAndCountryCode(userId, "NZ"))
            .thenReturn(Optional.of(existing))

        taxRateService.deleteTaxRate("nz") // lowercase

        verify(taxRateRepository).delete(existing)
    }
}