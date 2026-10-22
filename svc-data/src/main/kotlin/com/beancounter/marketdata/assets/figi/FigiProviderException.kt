package com.beancounter.marketdata.assets.figi

/**
 * OpenFIGI answered with an error (rate limit, auth) rather than a match or a no-match.
 */
class FigiProviderException(
    message: String
) : RuntimeException(message)