package com.beancounter.marketdata.providers.morningstar

import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Gateway for calling Morningstar's unofficial API.
 * The API is publicly accessible without authentication.
 */
@Component
class MorningstarGateway {
    private val restTemplate = RestTemplate()

    companion object {
        private val log = LoggerFactory.getLogger(MorningstarGateway::class.java)
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    /**
     * Fetches price data for a security using its Morningstar ID or ISIN.
     *
     * @param securityId The Morningstar ID (e.g., F00000P791) or ISIN (e.g., GB00B6WZJX05)
     * @param currencyId The currency for prices (default GBP)
     * @param startDate How far back to fetch prices
     * @return JSON response from Morningstar API
     */
    fun getPrice(
        securityId: String,
        currencyId: String = "GBP",
        startDate: LocalDate = LocalDate.now().minusDays(7)
    ): String? =
        try {
            // Determine if it's a Morningstar ID or ISIN
            val (idType, formattedId) =
                if (securityId.startsWith("F0")) {
                    // Morningstar ID format: F00000P791]2]0]FOGBR$$ALL
                    "Morningstar" to "$securityId]2]0]FOGBR\$\$ALL"
                } else {
                    // ISIN format
                    "isin" to securityId
                }

            val uri =
                UriComponentsBuilder
                    .fromUriString(MorningstarConfig.PRICE_API_URL)
                    .queryParam("currencyId", currencyId)
                    .queryParam("idtype", idType)
                    .queryParam("frequency", "daily")
                    .queryParam("outputType", "JSON")
                    .queryParam("startDate", startDate.format(DATE_FORMATTER))
                    .queryParam("id", formattedId)
                    .build()
                    .toUriString()

            val headers =
                HttpHeaders().apply {
                    set(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                    accept = listOf(MediaType.APPLICATION_JSON)
                }

            log.debug("Fetching price for {} from Morningstar", securityId)

            val response =
                restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    HttpEntity<Any>(headers),
                    String::class.java
                )

            response.body
        } catch (e: Exception) {
            log.error("Error fetching price for {}: {}", securityId, e.message)
            null
        }
}