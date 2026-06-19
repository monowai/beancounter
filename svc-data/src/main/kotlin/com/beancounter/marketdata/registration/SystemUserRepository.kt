package com.beancounter.marketdata.registration

import com.beancounter.common.model.SystemUser
import org.springframework.data.repository.CrudRepository
import java.util.Optional

/**
 * CRUD repo for SystemUser.  A SystemUser can own portfolios in BC.
 *
 * Finder parameters are nullable by design. Spring Data 4 introspects Kotlin
 * nullability metadata via kotlin-reflect and enforces it at invocation time;
 * a smart-cast non-null value (e.g. `email` after an `email != null` check) is
 * still seen as `String?` across the reflective repository call and a non-null
 * parameter throws `argument type mismatch: actual type 'String?', but 'String'
 * was expected`. Declaring the parameters nullable aligns the metadata. The
 * callers in [SystemUserCache] only ever pass non-null values, so the derived
 * query semantics are unchanged.
 */
interface SystemUserRepository : CrudRepository<SystemUser, String> {
    fun findByEmail(email: String?): Optional<SystemUser>

    fun findByAuth0(auth0: String?): Optional<SystemUser>

    fun findByGoogleId(google: String?): Optional<SystemUser>
}