package com.beancounter.marketdata.tax

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TaxRateRepository : JpaRepository<TaxRate, String> {
    fun findByOwnerIdAndCountryCode(
        ownerId: String,
        countryCode: String
    ): Optional<TaxRate>

    fun findAllByOwnerId(ownerId: String): List<TaxRate>

    fun deleteByOwnerIdAndCountryCode(
        ownerId: String,
        countryCode: String
    )
}