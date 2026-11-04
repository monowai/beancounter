package com.beancounter.marketdata.classification.enricher

import tools.jackson.databind.ObjectMapper

/**
 * What an AlphaVantage OVERVIEW or ETF_PROFILE body says before it is parsed: a quota signal,
 * an error, or a payload worth reading.
 */
enum class AlphaResponseBody {
    RATE_LIMITED,
    ERROR,
    PAYLOAD;

    companion object {
        /**
         * AlphaVantage signals quota exhaustion with a top-level `Information` or `Note` key
         * returned over HTTP 200 - not the usual "Error Message" body. Must be checked before
         * parsing: such a body deserializes cleanly into an empty response and would otherwise be
         * indistinguishable from [EnrichmentResult.NO_DATA], which is precisely the bug that let a
         * whole refresh run silently no-op.
         *
         * The wording is not stable - observed variants include "standard API rate limit is 25
         * requests per day", "lift the free key rate limit", and "our standard API call frequency
         * is 5 calls per minute". Rather than chase phrasing, treat the presence of either key as
         * disqualifying: neither ever appears in a valid OVERVIEW or ETF_PROFILE payload. An
         * invalid API key also lands here, and aborting the run is the right response to that too.
         */
        fun of(
            response: String,
            objectMapper: ObjectMapper
        ): AlphaResponseBody =
            when {
                isQuotaSignal(response, objectMapper) -> RATE_LIMITED
                response.isBlank() || response.contains("Error") -> ERROR
                else -> PAYLOAD
            }

        private fun isQuotaSignal(
            response: String,
            objectMapper: ObjectMapper
        ): Boolean {
            val root =
                runCatching { objectMapper.readTree(response) }.getOrNull()
                    ?: return false
            return root.has("Information") || root.has("Note")
        }
    }
}