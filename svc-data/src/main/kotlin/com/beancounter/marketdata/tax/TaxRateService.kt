package com.beancounter.marketdata.tax

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Service for managing user-defined tax rates by country.
 *
 * Each user can configure their own tax rates for different countries
 * where they hold income-generating assets.
 */
@Service
@Transactional
class TaxRateService(
    private val taxRateRepository: TaxRateRepository,
    private val systemUserService: SystemUserService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val PERCENTAGE_MULTIPLIER = 100
    }

    /**
     * Get all tax rates for the current user.
     */
    fun getMyTaxRates(): TaxRatesResponse {
        val user =
            systemUserService.getActiveUser()
                ?: return TaxRatesResponse(emptyList())
        val rates = taxRateRepository.findAllByOwnerId(user.id)
        return TaxRatesResponse(rates.map { it.toDto() })
    }

    /**
     * Get tax rate for a specific country.
     * Returns null if no rate is configured.
     */
    fun getTaxRate(countryCode: String): TaxRateResponse? {
        val user =
            systemUserService.getActiveUser()
                ?: throw BusinessException("User not authenticated")
        val normalizedCode = countryCode.uppercase()
        val rate =
            taxRateRepository
                .findByOwnerIdAndCountryCode(user.id, normalizedCode)
                .orElse(null) ?: return null
        return TaxRateResponse(rate.toDto())
    }

    /**
     * Get tax rate value for a country, defaulting to zero if not configured.
     * This is the method used by PrivateAssetConfig calculations.
     */
    fun getTaxRateValue(countryCode: String): BigDecimal {
        val user = systemUserService.getActiveUser() ?: return BigDecimal.ZERO
        val normalizedCode = countryCode.uppercase()
        return taxRateRepository
            .findByOwnerIdAndCountryCode(user.id, normalizedCode)
            .map { it.rate }
            .orElse(BigDecimal.ZERO)
    }

    /**
     * Save (create or update) a tax rate for a country.
     */
    fun saveTaxRate(request: TaxRateRequest): TaxRateResponse {
        val user =
            systemUserService.getActiveUser()
                ?: throw BusinessException("User not authenticated")

        val normalizedCode = request.countryCode.uppercase()
        validateCountryCode(normalizedCode)
        validateRate(request.rate)

        val existing =
            taxRateRepository
                .findByOwnerIdAndCountryCode(user.id, normalizedCode)
                .orElse(null)

        val taxRate =
            if (existing != null) {
                existing.copy(
                    rate = request.rate,
                    updatedDate = LocalDate.now()
                )
            } else {
                TaxRate(
                    owner = user,
                    countryCode = normalizedCode,
                    rate = request.rate,
                    createdDate = LocalDate.now(),
                    updatedDate = LocalDate.now()
                )
            }

        val saved = taxRateRepository.save(taxRate)
        log.info(
            "Saved tax rate for country {}: {}%",
            normalizedCode,
            request.rate.multiply(BigDecimal(PERCENTAGE_MULTIPLIER))
        )
        return TaxRateResponse(saved.toDto())
    }

    /**
     * Delete tax rate for a country.
     */
    fun deleteTaxRate(countryCode: String) {
        val user =
            systemUserService.getActiveUser()
                ?: throw BusinessException("User not authenticated")

        val normalizedCode = countryCode.uppercase()
        val existing =
            taxRateRepository
                .findByOwnerIdAndCountryCode(user.id, normalizedCode)
                .orElseThrow { NotFoundException("Tax rate not found for country: $normalizedCode") }

        taxRateRepository.delete(existing)
        log.info("Deleted tax rate for country: {}", normalizedCode)
    }

    private fun validateCountryCode(code: String) {
        if (code.length != 2 || !code.all { it.isLetter() }) {
            throw BusinessException("Country code must be 2 letters (ISO 3166-1 alpha-2)")
        }
    }

    private fun validateRate(rate: BigDecimal) {
        if (rate < BigDecimal.ZERO || rate > BigDecimal.ONE) {
            throw BusinessException("Tax rate must be between 0 and 1 (e.g., 0.20 for 20%)")
        }
    }

    private fun TaxRate.toDto() =
        TaxRateDto(
            countryCode = countryCode,
            rate = rate
        )
}