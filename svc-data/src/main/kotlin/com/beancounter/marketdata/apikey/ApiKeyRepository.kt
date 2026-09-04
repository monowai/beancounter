package com.beancounter.marketdata.apikey

import com.beancounter.common.model.ApiKey
import org.springframework.data.domain.Sort
import org.springframework.data.repository.CrudRepository

/**
 * CRUD for BC-issued API keys.
 *
 * Finder parameters are nullable by design. Spring Data 4 introspects Kotlin
 * nullability metadata via kotlin-reflect and enforces it at invocation time;
 * a smart-cast non-null value is still seen as the nullable type across the
 * reflective repository call and a non-null parameter throws `argument type
 * mismatch: actual type 'String?', but 'String' was expected` (see
 * SystemUserRepository for the full writeup). The callers here only ever
 * pass non-null values, so the derived query semantics are unchanged.
 */
interface ApiKeyRepository : CrudRepository<ApiKey, String> {
    fun findByOwnerId(
        ownerId: String?,
        sort: Sort = Sort.by(Sort.Order.desc("createdAt"))
    ): List<ApiKey>

    fun findByKeyHash(keyHash: String?): ApiKey?
}