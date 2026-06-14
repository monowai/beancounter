package com.beancounter.marketdata.assets.figi

/**
 * FIGI API contract.
 *
 * OpenFIGI /v3/mapping returns one of:
 *  - a match:    `{ "data": [ ... ] }`
 *  - a no-match: `{ "warning": "No identifier found." }` (both data and error null)
 *  - a hard error (rate-limit / auth): `{ "error": "..." }`
 */
data class FigiResponse(
    var data: Collection<FigiAsset>?,
    var error: String?,
    var warning: String? = null
)