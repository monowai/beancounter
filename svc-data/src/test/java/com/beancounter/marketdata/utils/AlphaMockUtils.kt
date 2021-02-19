package com.beancounter.marketdata.utils

import com.beancounter.common.utils.BcJson
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Alpha Vantage Mocking support.
 *
 * @author mikeh
 * @since 2019-03-09
 */
object AlphaMockUtils {
    const val alphaContracts = "/contracts/alpha"
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @JvmStatic
    private val alphaApi: WireMockRule = WireMockRule(WireMockConfiguration.options().port(7777))

    @JvmStatic
    fun getAlphaApi(): WireMockRule {
        if (!alphaApi.isRunning) {
            this.alphaApi.start()
            mockSearchResponse("MSFT",
                    ClassPathResource(alphaContracts + "/msft-response.json").file)
            mockSearchResponse("BRK-B",
                    ClassPathResource(alphaContracts + "/brkb-response.json").file)
            mockSearchResponse("AAPL",
                    ClassPathResource(alphaContracts + "/appl-response.json").file)
            mockSearchResponse("AMP.AX",
                    ClassPathResource(alphaContracts + "/amp-search.json").file)
            mockSearchResponse("DTV",
                    ClassPathResource(alphaContracts + "/dtv-search.json").file)
            mockGlobalResponse(
                    "AMP.AX", ClassPathResource(alphaContracts + "/amp-global.json").file)
            mockGlobalResponse(
                    "AMP.AUS", ClassPathResource(alphaContracts + "/amp-global.json").file)
            mockGlobalResponse(
                    "MSFT", ClassPathResource(alphaContracts + "/msft-global.json").file)

        }

        return this.alphaApi
    }

    /**
     * Convenience function to stub a response for ABC symbol.
     *
     * @param jsonFile     response file to return
     * @throws IOException anything
     */
    @Throws(IOException::class)
    fun mockAdjustedResponse(symbol: String, jsonFile: File?) {
        mockGetResponse("/query?function=TIME_SERIES_DAILY_ADJUSTED&symbol=$symbol&apikey=demo",
                jsonFile)
    }

    @Throws(IOException::class)
    fun mockGlobalResponse(symbol: String, jsonFile: File?) {
        mockGetResponse("/query?function=GLOBAL_QUOTE&symbol=$symbol&apikey=demo",
                jsonFile)
    }

    /**
     * Convenience function to stub a GET/200 response.
     *
     * @param url          url to stub
     * @param jsonFile     response file to return
     * @throws IOException anything
     */
    @Throws(IOException::class)
    fun mockGetResponse(url: String?, jsonFile: File?) {
        alphaApi.stubFor(
                WireMock.get(WireMock.urlEqualTo(url))
                        .willReturn(
                            WireMock.aResponse()
                                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .withBody(
                                    objectMapper.writeValueAsString(
                                        objectMapper.readValue(
                                            jsonFile,
                                            HashMap::class.java
                                        )
                                    )
                                )
                                .withStatus(200)))
    }

    @Throws(IOException::class)
    fun mockSearchResponse(code: String, response: File?) {
        alphaApi.stubFor(
                WireMock.get(WireMock.urlEqualTo("/query?function=SYMBOL_SEARCH&keywords=$code&apikey=demo"))
                        .willReturn(
                            WireMock.aResponse()
                                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .withBody(
                                    objectMapper.writeValueAsString(
                                        objectMapper.readValue(
                                            response,
                                            HashMap::class.java
                                        )
                                    )
                                )
                                .withStatus(200)))
    }
}