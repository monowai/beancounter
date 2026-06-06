package com.beancounter.marketdata.assets

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.AssetUtils.Companion.getAssetInput
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.utils.ASSET_ROOT
import com.beancounter.marketdata.utils.BcMvcHelper
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import com.beancounter.marketdata.utils.TRADE_DATE
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

/**
 * Verifies the admin DELETE /assets/admin/{assetId} endpoint introduced for
 * the Asset Lookup "delete + reload" flow on bc-view. The endpoint refuses
 * to delete an asset that is still referenced by any transaction (either as
 * the trade asset or as cash settlement) and requires SCOPE_ADMIN.
 */
@SpringMvcDbTest
internal class AdminAssetDeleteTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var dateUtils: DateUtils

    private lateinit var adminHelper: BcMvcHelper

    @BeforeEach
    fun setup() {
        val token =
            registerUser(
                mockMvc,
                mockAuthConfig.getUserToken(
                    SystemUser(id = "adminAssetDelete", email = "admin@assetdelete.test", auth0 = "adminAssetDelete")
                )
            )
        adminHelper = BcMvcHelper(mockMvc, token)
    }

    private fun createPublicAsset(code: String): String {
        val asset = getTestAsset(market = NASDAQ, code = code)
        val createMap = mapOf(toKey(asset) to getAssetInput(NASDAQ.code, asset.code))
        val mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post(ASSET_ROOT)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(adminHelper.token))
                        .with(csrf())
                        .content(objectMapper.writeValueAsString(AssetRequest(createMap)))
                        .contentType(APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()
        return objectMapper
            .readValue<AssetUpdateResponse>(mvcResult.response.contentAsString)
            .data[toKey(asset)]!!
            .id
    }

    @Test
    fun `admin can delete asset not held in any transaction`() {
        val assetId = createPublicAsset("ADMINDEL.A")

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .delete("$ASSET_ROOT/admin/{assetId}", assetId)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(adminHelper.token))
                    .with(csrf())
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("$ASSET_ROOT/{assetId}", assetId)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(adminHelper.token))
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `admin cannot delete asset that is held in a transaction`() {
        val assetId = createPublicAsset("ADMINDEL.HELD")
        val portfolio =
            adminHelper.portfolio(
                PortfolioInput("ADMDEL", "ADMDEL", currency = Constants.NZD.code)
            )
        adminHelper.postTrn(
            TrnRequest(
                portfolio.id,
                listOf(
                    TrnInput(
                        CallerRef(batch = "0", callerId = "held-1"),
                        assetId,
                        trnType = TrnType.BUY,
                        quantity = BigDecimal.TEN,
                        tradeDate = dateUtils.getFormattedDate(TRADE_DATE),
                        price = BigDecimal.TEN,
                        status = TrnStatus.SETTLED
                    )
                )
            )
        )

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .delete("$ASSET_ROOT/admin/{assetId}", assetId)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(adminHelper.token))
                    .with(csrf())
            ).andExpect(status().isBadRequest)
            .andExpect(
                jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("held in one or more transactions"))
            )
    }

    @Test
    fun `non-admin cannot delete via admin endpoint`() {
        val noRoles =
            mockAuthConfig.tokenUtils.getNoRolesToken(
                SystemUser(email = "noroles@assetdelete.test")
            )

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .delete("$ASSET_ROOT/admin/{assetId}", "any-id")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(noRoles))
                    .with(csrf())
            ).andExpect(status().isForbidden)
    }
}