package com.beancounter.marketdata.integ

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.utils.FigiMockUtils.getFigiApi
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@ExtendWith(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("figi")
@Tag("slow")
class FigiApiTest {
    @Autowired
    private val figiProxy: FigiProxy? = null

    @Autowired
    private val marketService: MarketService? = null

    @Autowired
    private val enrichmentFactory: EnrichmentFactory? = null

    @Autowired
    private lateinit var context: WebApplicationContext

    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Test
    fun is_CommonStockFound() {
        val asset = figiProxy!!.find(marketService!!.getMarket("NASDAQ"), "MSFT")
        assertThat(asset)
            .isNotNull
            .hasFieldOrPropertyWithValue("name", "MICROSOFT CORP")
            .isNotNull
    }

    @Test
    fun is_AdrFound() {
        val asset = figiProxy!!.find(marketService!!.getMarket("NASDAQ"), "BAIDU")
        assertThat(asset)
                .isNotNull
                .hasFieldOrPropertyWithValue("name", "BAIDU INC - SPON ADR")
                .isNotNull
    }

    @Test
    fun is_ReitFound() {
        val asset = figiProxy!!.find(marketService!!.getMarket("NYSE"), "OHI")
        assertThat(asset)
                .isNotNull
                .hasFieldOrPropertyWithValue("name", "OMEGA HEALTHCARE INVESTORS")
                .isNotNull
    }

    @Test
    fun is_MutualFundFound() {
        val asset = figiProxy!!.find(marketService!!.getMarket("NYSE"), "XLF")
        assertThat(asset)
                .isNotNull
                .hasFieldOrPropertyWithValue("name", "FINANCIAL SELECT SECTOR SPDR") // Unknown to BC, but is known to FIGI
                .hasNoNullFieldsOrPropertiesExcept("id", "priceSymbol")
                .isNotNull
    }

    @Test
    @Throws(Exception::class)
    fun is_BrkBFound() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

        // Authorise the caller to access the BC API
        val user = SystemUser("user", "user@testing.com")
        val token = TokenUtils.getUserToken(user)
        registerUser(mockMvc, token)
        val market = marketService!!.getMarket("NYSE")
        assertThat(market).isNotNull.hasFieldOrPropertyWithValue("enricher", null)
        // System default enricher is found
        assertThat(enrichmentFactory!!.getEnricher(market)).isNotNull
        val mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.get("/assets/{market}/{code}", "NYSE", "BRK.B")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(AuthorityRoleConverter()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper
                .readValue(mvcResult.response.contentAsString, AssetResponse::class.java)
        assertThat(data)
                .isNotNull
                .hasFieldOrPropertyWithValue("code", "BRK.B")
                .hasFieldOrPropertyWithValue("name", "BERKSHIRE HATHAWAY INC-CL B")
    }

    companion object {
        val figi = getFigiApi()

        @BeforeAll
        @JvmStatic
        fun is_ApiRunning() {
            assertThat(figi).isNotNull
            assertThat(figi.isRunning).isTrue()
        }

    }
}