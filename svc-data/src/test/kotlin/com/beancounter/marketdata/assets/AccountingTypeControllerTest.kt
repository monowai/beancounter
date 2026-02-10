package com.beancounter.marketdata.assets

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.AccountingTypeResponse
import com.beancounter.common.contracts.AccountingTypesResponse
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.SpringMvcDbTest
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val ROOT = "/admin/accounting-types"

@SpringMvcDbTest
internal class AccountingTypeControllerTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Test
    fun `create and list accounting types`() {
        val result =
            mockMvc
                .perform(
                    post(ROOT)
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(
                            """{"category":"EQUITY","currency":"USD","boardLot":100,"settlementDays":2}"""
                        )
                ).andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()

        val created = objectMapper.readValue<AccountingTypeResponse>(result.response.contentAsString)
        assertThat(created.data.category).isEqualTo("EQUITY")
        assertThat(created.data.currency.code).isEqualTo("USD")
        assertThat(created.data.boardLot).isEqualTo(100)
        assertThat(created.data.settlementDays).isEqualTo(2)

        val listResult =
            mockMvc
                .perform(
                    get(ROOT)
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                ).andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()

        val types = objectMapper.readValue<AccountingTypesResponse>(listResult.response.contentAsString)
        assertThat(types.data).isNotEmpty
        assertThat(types.data.any { it.id == created.data.id }).isTrue()
    }

    @Test
    fun `duplicate category and currency returns existing`() {
        val body = """{"category":"BOND","currency":"NZD"}"""

        val first =
            mockMvc
                .perform(
                    post(ROOT)
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(body)
                ).andExpect(status().isOk)
                .andReturn()

        val second =
            mockMvc
                .perform(
                    post(ROOT)
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(body)
                ).andExpect(status().isOk)
                .andReturn()

        val firstType = objectMapper.readValue<AccountingTypeResponse>(first.response.contentAsString)
        val secondType = objectMapper.readValue<AccountingTypeResponse>(second.response.contentAsString)
        assertThat(firstType.data.id).isEqualTo(secondType.data.id)
    }

    @Test
    fun `get by id`() {
        val createResult =
            mockMvc
                .perform(
                    post(ROOT)
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""{"category":"ETF","currency":"GBP"}""")
                ).andExpect(status().isOk)
                .andReturn()

        val created = objectMapper.readValue<AccountingTypeResponse>(createResult.response.contentAsString)

        val getResult =
            mockMvc
                .perform(
                    get("$ROOT/${created.data.id}")
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                ).andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()

        val fetched = objectMapper.readValue<AccountingTypeResponse>(getResult.response.contentAsString)
        assertThat(fetched.data.id).isEqualTo(created.data.id)
        assertThat(fetched.data.category).isEqualTo("ETF")
    }

    @Test
    fun `patch updates boardLot and settlementDays`() {
        val createResult =
            mockMvc
                .perform(
                    post(ROOT)
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""{"category":"CASH","currency":"SGD"}""")
                ).andExpect(status().isOk)
                .andReturn()

        val created = objectMapper.readValue<AccountingTypeResponse>(createResult.response.contentAsString)
        assertThat(created.data.boardLot).isEqualTo(1)

        val patchResult =
            mockMvc
                .perform(
                    patch("$ROOT/${created.data.id}")
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""{"boardLot":50,"settlementDays":3}""")
                ).andExpect(status().isOk)
                .andExpect(content().contentType(APPLICATION_JSON))
                .andReturn()

        val updated = objectMapper.readValue<AccountingTypeResponse>(patchResult.response.contentAsString)
        assertThat(updated.data.boardLot).isEqualTo(50)
        assertThat(updated.data.settlementDays).isEqualTo(3)
    }

    @Test
    fun `delete unused accounting type`() {
        val createResult =
            mockMvc
                .perform(
                    post(ROOT)
                        .with(jwt().jwt(mockAuthConfig.getUserToken()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""{"category":"REIT","currency":"AUD"}""")
                ).andExpect(status().isOk)
                .andReturn()

        val created = objectMapper.readValue<AccountingTypeResponse>(createResult.response.contentAsString)

        mockMvc
            .perform(
                delete("$ROOT/${created.data.id}")
                    .with(jwt().jwt(mockAuthConfig.getUserToken()))
                    .with(csrf())
            ).andExpect(status().isNoContent)

        mockMvc
            .perform(
                get("$ROOT/${created.data.id}")
                    .with(jwt().jwt(mockAuthConfig.getUserToken()))
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `get nonexistent id returns 404`() {
        mockMvc
            .perform(
                get("$ROOT/does-not-exist")
                    .with(jwt().jwt(mockAuthConfig.getUserToken()))
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `unauthenticated request returns 401`() {
        mockMvc
            .perform(get(ROOT))
            .andExpect(status().isUnauthorized)
    }
}