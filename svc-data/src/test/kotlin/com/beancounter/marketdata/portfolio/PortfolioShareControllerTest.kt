package com.beancounter.marketdata.portfolio

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.PendingSharesResponse
import com.beancounter.common.contracts.PortfolioShareResponse
import com.beancounter.common.contracts.PortfolioSharesResponse
import com.beancounter.common.contracts.ShareInviteRequest
import com.beancounter.common.contracts.ShareRequestAccess
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.ShareAccessLevel
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.utils.BcMvcHelper.Companion.portfolioCreate
import com.beancounter.marketdata.utils.RegistrationUtils.registerUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val SHARES_ROOT = "/shares"

/**
 * Integration tests for portfolio sharing between clients and advisers.
 */
@SpringMvcDbTest
internal class PortfolioShareControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private lateinit var clientToken: Jwt
    private lateinit var adviserToken: Jwt

    @BeforeEach
    fun setup() {
        clientToken =
            registerUser(
                mockMvc,
                mockAuthConfig.getUserToken(
                    SystemUser(
                        id = "client-user",
                        email = "client@testing.com",
                        auth0 = "auth0|client"
                    )
                )
            )
        adviserToken =
            registerUser(
                mockMvc,
                mockAuthConfig.getUserToken(
                    SystemUser(
                        id = "adviser-user",
                        email = "adviser@testing.com",
                        auth0 = "auth0|adviser"
                    )
                )
            )
    }

    @Test
    fun `client invites adviser to view portfolio`() {
        val portfolio =
            portfolioCreate(
                PortfolioInput("SHARE_TEST", "Share Test", USD.code, NZD.code),
                mockMvc,
                clientToken
            ).data.first()

        val inviteRequest =
            ShareInviteRequest(
                portfolioIds = listOf(portfolio.id),
                adviserEmail = "adviser@testing.com"
            )

        val result =
            mockMvc
                .perform(
                    post("$SHARES_ROOT/invite")
                        .with(jwt().jwt(clientToken))
                        .with(csrf())
                        .content(objectMapper.writeValueAsBytes(inviteRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()

        val response =
            objectMapper.readValue(
                result.response.contentAsString,
                PortfolioSharesResponse::class.java
            )
        assertThat(response.data).hasSize(1)
        val share = response.data.first()
        assertThat(share.status).isEqualTo(ShareStatus.PENDING_CLIENT_INVITE)
        assertThat(share.accessLevel).isEqualTo(ShareAccessLevel.FULL)
        assertThat(share.portfolio?.id).isEqualTo(portfolio.id)
    }

    @Test
    fun `adviser accepts client invite`() {
        val portfolio =
            portfolioCreate(
                PortfolioInput("ACCEPT_TEST", "Accept Test", USD.code, NZD.code),
                mockMvc,
                clientToken
            ).data.first()

        val inviteRequest =
            ShareInviteRequest(
                portfolioIds = listOf(portfolio.id),
                adviserEmail = "adviser@testing.com"
            )

        val inviteResult =
            mockMvc
                .perform(
                    post("$SHARES_ROOT/invite")
                        .with(jwt().jwt(clientToken))
                        .with(csrf())
                        .content(objectMapper.writeValueAsBytes(inviteRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val shareId =
            objectMapper
                .readValue(
                    inviteResult.response.contentAsString,
                    PortfolioSharesResponse::class.java
                ).data
                .first()
                .id

        val acceptResult =
            mockMvc
                .perform(
                    post("$SHARES_ROOT/$shareId/accept")
                        .with(jwt().jwt(adviserToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val accepted =
            objectMapper
                .readValue(
                    acceptResult.response.contentAsString,
                    PortfolioShareResponse::class.java
                ).data
        assertThat(accepted.status).isEqualTo(ShareStatus.ACTIVE)
        assertThat(accepted.acceptedAt).isNotNull()
    }

    @Test
    fun `adviser can view shared portfolio after acceptance`() {
        val portfolio =
            portfolioCreate(
                PortfolioInput("VIEW_SHARED", "View Shared", USD.code, NZD.code),
                mockMvc,
                clientToken
            ).data.first()

        // Client invites adviser
        val inviteRequest =
            ShareInviteRequest(
                portfolioIds = listOf(portfolio.id),
                adviserEmail = "adviser@testing.com"
            )
        val inviteResult =
            mockMvc
                .perform(
                    post("$SHARES_ROOT/invite")
                        .with(jwt().jwt(clientToken))
                        .with(csrf())
                        .content(objectMapper.writeValueAsBytes(inviteRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val shareId =
            objectMapper
                .readValue(
                    inviteResult.response.contentAsString,
                    PortfolioSharesResponse::class.java
                ).data
                .first()
                .id

        // Adviser accepts
        mockMvc
            .perform(
                post("$SHARES_ROOT/$shareId/accept")
                    .with(jwt().jwt(adviserToken))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        // Adviser can now view the portfolio
        mockMvc
            .perform(
                get("/portfolios/${portfolio.id}")
                    .with(jwt().jwt(adviserToken))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)
    }

    @Test
    fun `adviser cannot view portfolio before acceptance`() {
        val portfolio =
            portfolioCreate(
                PortfolioInput("NO_VIEW", "No View", USD.code, NZD.code),
                mockMvc,
                clientToken
            ).data.first()

        // Adviser cannot view without share
        mockMvc
            .perform(
                get("/portfolios/${portfolio.id}")
                    .with(jwt().jwt(adviserToken))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().is4xxClientError)
    }

    @Test
    fun `adviser requests access from client`() {
        val request =
            ShareRequestAccess(
                clientEmail = "client@testing.com",
                message = "Please share your portfolios"
            )

        val result =
            mockMvc
                .perform(
                    post("$SHARES_ROOT/request")
                        .with(jwt().jwt(adviserToken))
                        .with(csrf())
                        .content(objectMapper.writeValueAsBytes(request))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()

        val response =
            objectMapper.readValue(
                result.response.contentAsString,
                PortfolioShareResponse::class.java
            )
        assertThat(response.data.status).isEqualTo(ShareStatus.PENDING_ADVISER_REQUEST)
        assertThat(response.data.portfolio).isNull()
    }

    @Test
    fun `pending notifications include invites and requests`() {
        val portfolio =
            portfolioCreate(
                PortfolioInput("PENDING_TEST", "Pending Test", USD.code, NZD.code),
                mockMvc,
                clientToken
            ).data.first()

        // Client invites adviser
        val inviteRequest =
            ShareInviteRequest(
                portfolioIds = listOf(portfolio.id),
                adviserEmail = "adviser@testing.com"
            )
        mockMvc
            .perform(
                post("$SHARES_ROOT/invite")
                    .with(jwt().jwt(clientToken))
                    .with(csrf())
                    .content(objectMapper.writeValueAsBytes(inviteRequest))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        // Adviser sees pending invite
        val adviserPending =
            mockMvc
                .perform(
                    get("$SHARES_ROOT/pending")
                        .with(jwt().jwt(adviserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val adviserNotifications =
            objectMapper.readValue(
                adviserPending.response.contentAsString,
                PendingSharesResponse::class.java
            )
        assertThat(adviserNotifications.invites).isNotEmpty
    }

    @Test
    fun `revoke active share`() {
        val portfolio =
            portfolioCreate(
                PortfolioInput("REVOKE_TEST", "Revoke Test", USD.code, NZD.code),
                mockMvc,
                clientToken
            ).data.first()

        // Create and accept share
        val inviteRequest =
            ShareInviteRequest(
                portfolioIds = listOf(portfolio.id),
                adviserEmail = "adviser@testing.com"
            )
        val inviteResult =
            mockMvc
                .perform(
                    post("$SHARES_ROOT/invite")
                        .with(jwt().jwt(clientToken))
                        .with(csrf())
                        .content(objectMapper.writeValueAsBytes(inviteRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val shareId =
            objectMapper
                .readValue(
                    inviteResult.response.contentAsString,
                    PortfolioSharesResponse::class.java
                ).data
                .first()
                .id

        mockMvc
            .perform(
                post("$SHARES_ROOT/$shareId/accept")
                    .with(jwt().jwt(adviserToken))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        // Client revokes share
        val revokeResult =
            mockMvc
                .perform(
                    delete("$SHARES_ROOT/$shareId")
                        .with(jwt().jwt(clientToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val revoked =
            objectMapper
                .readValue(
                    revokeResult.response.contentAsString,
                    PortfolioShareResponse::class.java
                ).data
        assertThat(revoked.status).isEqualTo(ShareStatus.REVOKED)
    }

    @Test
    fun `cannot share portfolio with yourself`() {
        val portfolio =
            portfolioCreate(
                PortfolioInput("SELF_SHARE", "Self Share", USD.code, NZD.code),
                mockMvc,
                clientToken
            ).data.first()

        val inviteRequest =
            ShareInviteRequest(
                portfolioIds = listOf(portfolio.id),
                adviserEmail = "client@testing.com"
            )

        mockMvc
            .perform(
                post("$SHARES_ROOT/invite")
                    .with(jwt().jwt(clientToken))
                    .with(csrf())
                    .content(objectMapper.writeValueAsBytes(inviteRequest))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().is4xxClientError)
    }

    @Test
    fun `email addresses are masked in pending notifications`() {
        val portfolio =
            portfolioCreate(
                PortfolioInput("MASK_TEST", "Mask Test", USD.code, NZD.code),
                mockMvc,
                clientToken
            ).data.first()

        val inviteRequest =
            ShareInviteRequest(
                portfolioIds = listOf(portfolio.id),
                adviserEmail = "adviser@testing.com"
            )
        mockMvc
            .perform(
                post("$SHARES_ROOT/invite")
                    .with(jwt().jwt(clientToken))
                    .with(csrf())
                    .content(objectMapper.writeValueAsBytes(inviteRequest))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        // Adviser sees masked client email in invite
        val adviserPending =
            mockMvc
                .perform(
                    get("$SHARES_ROOT/pending")
                        .with(jwt().jwt(adviserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val notifications =
            objectMapper.readValue(
                adviserPending.response.contentAsString,
                PendingSharesResponse::class.java
            )
        val invite = notifications.invites.first()
        val ownerEmail = invite.portfolio?.owner?.email ?: ""
        assertThat(ownerEmail).contains("*")
        assertThat(ownerEmail).doesNotContain("client@testing.com")
    }

    @Test
    fun `managed portfolios returns active shares for adviser`() {
        val portfolio =
            portfolioCreate(
                PortfolioInput("MANAGED_TEST", "Managed Test", USD.code, NZD.code),
                mockMvc,
                clientToken
            ).data.first()

        // Create and accept share
        val inviteRequest =
            ShareInviteRequest(
                portfolioIds = listOf(portfolio.id),
                adviserEmail = "adviser@testing.com"
            )
        val inviteResult =
            mockMvc
                .perform(
                    post("$SHARES_ROOT/invite")
                        .with(jwt().jwt(clientToken))
                        .with(csrf())
                        .content(objectMapper.writeValueAsBytes(inviteRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val shareId =
            objectMapper
                .readValue(
                    inviteResult.response.contentAsString,
                    PortfolioSharesResponse::class.java
                ).data
                .first()
                .id

        mockMvc
            .perform(
                post("$SHARES_ROOT/$shareId/accept")
                    .with(jwt().jwt(adviserToken))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        // Adviser sees managed portfolios
        val managedResult =
            mockMvc
                .perform(
                    get("$SHARES_ROOT/managed")
                        .with(jwt().jwt(adviserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val managed =
            objectMapper.readValue(
                managedResult.response.contentAsString,
                PortfolioSharesResponse::class.java
            )
        assertThat(managed.data).isNotEmpty
        assertThat(managed.data.map { it.portfolio?.code }).contains("MANAGED_TEST")
    }
}