package com.beancounter.marketdata.registration

import com.beancounter.common.contracts.UserPreferencesRequest
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.UserPreferences
import jakarta.transaction.Transactional
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
     */
    fun getOrCreate(owner: SystemUser): UserPreferences =
        userPreferencesRepository.findByOwner(owner).orElseGet {
            userPreferencesRepository.save(UserPreferences(owner = owner))
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
        return userPreferencesRepository.save(preferences)
    }
}