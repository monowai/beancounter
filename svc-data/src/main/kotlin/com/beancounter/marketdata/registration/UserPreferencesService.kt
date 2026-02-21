package com.beancounter.marketdata.registration

import com.beancounter.common.contracts.UserPreferencesRequest
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.UserPreferences
import jakarta.transaction.Transactional
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service

/**
 * Service for managing user preferences.
 */
@Service
@Transactional
class UserPreferencesService(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    /**
     * Get or create preferences for the given user.
     * Creates default preferences if none exist.
     * Handles race conditions where multiple requests try to create simultaneously.
     */
    fun getOrCreate(owner: SystemUser): UserPreferences =
        userPreferencesRepository.findByOwner(owner).orElseGet {
            try {
                userPreferencesRepository.save(UserPreferences(owner = owner))
            } catch (_: DataIntegrityViolationException) {
                // Race condition - another request created it first, just fetch it
                userPreferencesRepository.findByOwner(owner).orElseThrow {
                    IllegalStateException("Failed to get or create preferences for ${owner.id}")
                }
            }
        }

    /**
     * Update user preferences.
     * Only non-null fields in the request will be updated.
     */
    fun update(
        owner: SystemUser,
        request: UserPreferencesRequest
    ): UserPreferences {
        val preferences = getOrCreate(owner)
        request.preferredName?.let { preferences.preferredName = it }
        request.defaultHoldingsView?.let { preferences.defaultHoldingsView = it }
        request.defaultValueIn?.let { preferences.defaultValueIn = it }
        request.defaultGroupBy?.let { preferences.defaultGroupBy = it }
        request.baseCurrencyCode?.let { preferences.baseCurrencyCode = it }
        request.reportingCurrencyCode?.let { preferences.reportingCurrencyCode = it }
        request.showWeightedIrr?.let { preferences.showWeightedIrr = it }
        request.enableTwr?.let { preferences.enableTwr = it }
        return userPreferencesRepository.save(preferences)
    }
}