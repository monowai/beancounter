package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.io.File
import java.io.IOException

/**
 * Alpha Vantage Mocking support.
 *
 * @author mikeh
 * @since 2019-03-09
 */
object AlphaMockUtils {
    const val ALPHA_MOCK = "mock/alpha"
    const val URL_ASSETS_MARKET_CODE = "/assets/{market}/{code}"

    @JvmStatic
    fun mockAlphaAssets() {
        mockSearchResponse(
            MSFT.code,
            ClassPathResource("$ALPHA_MOCK/msft-response.json").file
        )
        mockSearchResponse(
            "BRK-B",
            ClassPathResource("$ALPHA_MOCK/brkb-response.json").file
        )
        mockSearchResponse(
            "AAPL",
            ClassPathResource("$ALPHA_MOCK/aapl-response.json").file
        )
        mockSearchResponse(
            "AMP.AX",
            ClassPathResource("$ALPHA_MOCK/amp-search.json").file
        )
        mockSearchResponse(
            "DTV",
            ClassPathResource("$ALPHA_MOCK/dtv-search.json").file
        )
        mockGlobalResponse(
            "AMP.AX",
            ClassPathResource("$ALPHA_MOCK/amp-global.json").file
        )
        mockGlobalResponse(
            "AMP.AUS",
            ClassPathResource("$ALPHA_MOCK/amp-global.json").file
        )
        mockGlobalResponse(
            MSFT.code,
            ClassPathResource("$ALPHA_MOCK/msft-global.json").file
        )
    }

    /**
     * Convenience function to stub a response for ABC symbol.
     *
     * @param jsonFile     response file to return
     * @throws IOException anything
     */
    fun mockAdjustedResponse(
        symbol: String,
        jsonFile: File
    ) {
        mockGetResponse(
            "/query?function=TIME_SERIES_DAILY_ADJUSTED&symbol=$symbol&apikey=demo&outputsize=full",
            jsonFile
        )
    }

    fun mockGlobalResponse(
        symbol: String,
        jsonFile: File
    ) {
        mockGetResponse(
            "/query?function=GLOBAL_QUOTE&symbol=$symbol&apikey=demo",
            jsonFile
        )
    }

    /**
     * Convenience function to stub a GET/200 response.
     *
     * @param url          url to stub
     * @param jsonFile     response file to return
     * @throws IOException anything
     */
    private fun mockGetResponse(
        url: String,
        jsonFile: File
    ) {
        stubFor(
            WireMock
                .get(WireMock.urlEqualTo(url))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withHeader(
                            HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE
                        ).withBody(
                            objectMapper.writeValueAsString(
                                objectMapper.readValue(
                                    jsonFile,
                                    HashMap::class.java
                                )
                            )
                        ).withStatus(200)
                )
        )
    }

    fun mockSearchResponse(
        code: String,
        file: File
    ) {
        stubFor(
            WireMock
                .get(
                    WireMock.urlEqualTo("/query?function=SYMBOL_SEARCH&keywords=$code&apikey=demo")
                ).willReturn(
                    WireMock
                        .aResponse()
                        .withHeader(
                            HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE
                        ).withBody(
                            objectMapper.writeValueAsString(
                                objectMapper.readValue(
                                    file,
                                    HashMap::class.java
                                )
                            )
                        ).withStatus(200)
                )
        )
    }
}