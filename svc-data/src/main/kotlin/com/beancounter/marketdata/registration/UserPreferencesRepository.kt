package com.beancounter.marketdata.registration

import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.UserPreferences
import org.springframework.data.repository.CrudRepository
import java.util.Optional

/**
 * CRUD repo for UserPreferences.
 */
interface UserPreferencesRepository : CrudRepository<UserPreferences, String> {
    fun findByOwner(owner: SystemUser): Optional<UserPreferences>
}