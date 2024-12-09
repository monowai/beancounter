package com.beancounter.marketdata.providers.figi

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.P_CODE
import com.beancounter.marketdata.Constants.Companion.P_NAME
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.assets.figi.FigiResponse
import com.beancounter.marketdata.assets.figi.FigiSearch
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.ASSET_ROOT
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import com.fasterxml.jackson.core.type.TypeReference
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.io.File

private const val BRK_B = "BRK.B"

/**
 * Bloomberg OpenFigi via mocks.
 */
@SpringBootTest
@ActiveProfiles("figi")
@Tag("wiremock")
@AutoConfigureWireMock(port = 0)
@AutoConfigureMockAuth
class FigiAssetApiTest {
    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var figiProxy: FigiProxy

    @Autowired
    private lateinit var marketService: MarketService

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var environment: Environment

    @Test
    fun is_CommonStockFound() {
        val asset =
            figiProxy.find(
                marketService.getMarket(NASDAQ.code),
                MSFT.code
            )
        assertThat(asset)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                P_NAME,
                "MICROSOFT CORP"
            ).isNotNull
    }

    @Test
    fun is_AdrFound() {
        val asset =
            figiProxy.find(
                marketService.getMarket(NASDAQ.code),
                "BAIDU"
            )
        assertThat(asset)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                P_NAME,
                "BAIDU INC - SPON ADR"
            ).isNotNull
    }

    @Test
    fun is_ReitFound() {
        val asset =
            figiProxy.find(
                marketService.getMarket(NYSE.code),
                "OHI"
            )
        assertThat(asset)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                P_NAME,
                "OMEGA HEALTHCARE INVESTORS"
            ).isNotNull
    }

    @Test
    fun is_MutualFundFound() {
        val asset =
            figiProxy.find(
                marketService.getMarket(NYSE.code),
                "XLF"
            )
        assertThat(asset)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                P_NAME,
                "FINANCIAL SELECT SECTOR SPDR"
            ) // Unknown to BC, but is known to FIGI
            .hasNoNullFieldsOrPropertiesExcept(
                "id",
                "priceSymbol",
                "systemUser"
            ).isNotNull
    }

    @Test
    fun is_BrkBFound() {
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

        // Authorise the caller to access the BC API
        val token = mockAuthConfig.getUserToken(Constants.systemUser)
        registerUser(
            mockMvc,
            token
        )
        val market = marketService.getMarket(NYSE.code)
        assertThat(market).isNotNull.hasFieldOrPropertyWithValue(
            "enricher",
            null
        )
        // System default enricher is found
        assertThat(enrichmentFactory.getEnricher(market)).isNotNull

        val mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(
                            "$ASSET_ROOT/{market}/{code}",
                            NYSE.code,
                            BRK_B
                        ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) =
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    AssetResponse::class.java
                )
        assertThat(data)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                P_CODE,
                BRK_B
            ).hasFieldOrPropertyWithValue(
                P_NAME,
                "BERKSHIRE HATHAWAY INC-CL B"
            )
    }

    @Test
    fun is_DefaultAssetName() {
        //
        val assetRequest =
            AssetRequest(
                AssetInput(
                    NASDAQ.code,
                    "ABC",
                    "My Default Name"
                ),
                "ZZZ"
            )
        val assetResponse = assetService.handle(assetRequest)
        assertThat(assetResponse).isNotNull
        assertThat(assetResponse.data)
            .hasSize(1)
            .containsKey(
                assetRequest.data
                    .iterator()
                    .next()
                    .key
            )

        val createdAsset =
            assetResponse.data
                .iterator()
                .next()
                .value
        assertThat(createdAsset)
            .hasFieldOrPropertyWithValue(
                P_NAME,
                createdAsset.name
            ).hasFieldOrPropertyWithValue(
                P_CODE,
                createdAsset.code
            )
    }

    @BeforeEach
    fun mockApis() {
        val prefix = "/mock/figi"

        mock(
            ClassPathResource("$prefix/common-stock-response.json").file,
            MSFT.code,
            "Common Stock"
        )

        mock(
            ClassPathResource("$prefix/adr-response.json").file,
            "BAIDU",
            "Depositary Receipt"
        )

        mock(
            ClassPathResource("$prefix/reit-response.json").file,
            "OHI",
            "REIT"
        )

        mock(
            ClassPathResource("$prefix/mf-response.json").file,
            "XLF",
            "REIT"
        )

        mock(
            ClassPathResource("$prefix/brkb-response.json").file,
            "BRK/B",
            "Common Stock"
        )
        stubFor(
            WireMock
                .any(WireMock.anyUrl())
                .atPriority(10)
                .willReturn(
                    WireMock
                        .aResponse()
                        .withStatus(200)
                        .withHeader(
                            HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE
                        ).withBody(
                            "[{\"error\": \"No identifier found.\"\n" +
                                "    }\n" +
                                "]"
                        )
                )
        )
    }

    companion object {
        @JvmStatic
        fun mock(
            jsonFile: File,
            code: String,
            securityType: String
        ) {
            val search =
                FigiSearch(
                    code,
                    "US",
                    securityType,
                    true
                )
            val searchCollection: MutableCollection<FigiSearch> = ArrayList()
            searchCollection.add(search)
            val response: Collection<FigiResponse> =
                objectMapper.readValue(
                    jsonFile,
                    object : TypeReference<Collection<FigiResponse>>() {}
                )
            stubFor(
                WireMock
                    .post(WireMock.urlEqualTo("/v2/mapping"))
                    .withRequestBody(
                        WireMock.equalToJson(objectMapper.writeValueAsString(searchCollection))
                    ).withHeader(
                        "X-OPENFIGI-APIKEY",
                        WireMock.matching("demoxx")
                    ).willReturn(
                        WireMock
                            .aResponse()
                            .withHeader(
                                HttpHeaders.CONTENT_TYPE,
                                MediaType.APPLICATION_JSON_VALUE
                            ).withBody(objectMapper.writeValueAsString(response))
                            .withStatus(200)
                    )
            )
        }
    }
}