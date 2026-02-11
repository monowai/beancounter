package com.beancounter.marketdata.share

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.PendingResourceSharesResponse
import com.beancounter.common.contracts.ResourceShareInviteRequest
import com.beancounter.common.contracts.ResourceShareRequestAccess
import com.beancounter.common.contracts.ResourceShareResponse
import com.beancounter.common.contracts.ResourceSharesResponse
import com.beancounter.common.contracts.ShareAccessCheck
import com.beancounter.common.model.ShareAccessLevel
import com.beancounter.common.model.ShareResourceType
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.SpringMvcDbTest
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

private const val RESOURCE_SHARES_ROOT = "/resource-shares"

/**
 * Integration tests for resource sharing (independence plans and rebalance models)
 * between clients and advisers.
 */
@SpringMvcDbTest
internal class ResourceShareControllerTest {
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
                        id = "rs-client",
                        email = "rs-client@testing.com",
                        auth0 = "auth0|rs-client"
                    )
                )
            )
        adviserToken =
            registerUser(
                mockMvc,
                mockAuthConfig.getUserToken(
                    SystemUser(
                        id = "rs-adviser",
                        email = "rs-adviser@testing.com",
                        auth0 = "auth0|rs-adviser"
                    )
                )
            )
    }

    @Test
    fun `client invites adviser to view plan`() {
        val request =
            ResourceShareInviteRequest(
                resourceType = ShareResourceType.INDEPENDENCE_PLAN,
                resourceIds = listOf("plan-001"),
                adviserEmail = "rs-adviser@testing.com",
                accessLevel = ShareAccessLevel.VIEW
            )

        val result =
            mockMvc
                .perform(
                    post("$RESOURCE_SHARES_ROOT/invite")
                        .with(jwt().jwt(clientToken))
                        .with(csrf())
                        .content(objectMapper.writeValueAsBytes(request))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()

        val response =
            objectMapper.readValue(
                result.response.contentAsString,
                ResourceSharesResponse::class.java
            )
        assertThat(response.data).hasSize(1)
        val share = response.data.first()
        assertThat(share.status).isEqualTo(ShareStatus.PENDING_CLIENT_INVITE)
        assertThat(share.accessLevel).isEqualTo(ShareAccessLevel.VIEW)
        assertThat(share.resourceType).isEqualTo(ShareResourceType.INDEPENDENCE_PLAN)
        assertThat(share.resourceId).isEqualTo("plan-001")
    }

    @Test
    fun `adviser accepts client invite for plan`() {
        val inviteRequest =
            ResourceShareInviteRequest(
                resourceType = ShareResourceType.INDEPENDENCE_PLAN,
                resourceIds = listOf("plan-accept"),
                adviserEmail = "rs-adviser@testing.com"
            )

        val inviteResult =
            mockMvc
                .perform(
                    post("$RESOURCE_SHARES_ROOT/invite")
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
                    ResourceSharesResponse::class.java
                ).data
                .first()
                .id

        val acceptResult =
            mockMvc
                .perform(
                    post("$RESOURCE_SHARES_ROOT/$shareId/accept")
                        .with(jwt().jwt(adviserToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val accepted =
            objectMapper
                .readValue(
                    acceptResult.response.contentAsString,
                    ResourceShareResponse::class.java
                ).data
        assertThat(accepted.status).isEqualTo(ShareStatus.ACTIVE)
        assertThat(accepted.acceptedAt).isNotNull()
    }

    @Test
    fun `adviser requests access from client`() {
        val request =
            ResourceShareRequestAccess(
                resourceType = ShareResourceType.REBALANCE_MODEL,
                clientEmail = "rs-client@testing.com",
                message = "Please share your models"
            )

        val result =
            mockMvc
                .perform(
                    post("$RESOURCE_SHARES_ROOT/request")
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
                ResourceShareResponse::class.java
            )
        assertThat(response.data.status).isEqualTo(ShareStatus.PENDING_ADVISER_REQUEST)
        assertThat(response.data.resourceType).isEqualTo(ShareResourceType.REBALANCE_MODEL)
    }

    @Test
    fun `pending notifications include invites and requests`() {
        val inviteRequest =
            ResourceShareInviteRequest(
                resourceType = ShareResourceType.INDEPENDENCE_PLAN,
                resourceIds = listOf("plan-pending"),
                adviserEmail = "rs-adviser@testing.com"
            )
        mockMvc
            .perform(
                post("$RESOURCE_SHARES_ROOT/invite")
                    .with(jwt().jwt(clientToken))
                    .with(csrf())
                    .content(objectMapper.writeValueAsBytes(inviteRequest))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        val pendingResult =
            mockMvc
                .perform(
                    get("$RESOURCE_SHARES_ROOT/pending")
                        .with(jwt().jwt(adviserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val notifications =
            objectMapper.readValue(
                pendingResult.response.contentAsString,
                PendingResourceSharesResponse::class.java
            )
        assertThat(notifications.invites).isNotEmpty
    }

    @Test
    fun `revoke active resource share`() {
        val inviteRequest =
            ResourceShareInviteRequest(
                resourceType = ShareResourceType.REBALANCE_MODEL,
                resourceIds = listOf("model-revoke"),
                adviserEmail = "rs-adviser@testing.com"
            )
        val inviteResult =
            mockMvc
                .perform(
                    post("$RESOURCE_SHARES_ROOT/invite")
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
                    ResourceSharesResponse::class.java
                ).data
                .first()
                .id

        mockMvc
            .perform(
                post("$RESOURCE_SHARES_ROOT/$shareId/accept")
                    .with(jwt().jwt(adviserToken))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        val revokeResult =
            mockMvc
                .perform(
                    delete("$RESOURCE_SHARES_ROOT/$shareId")
                        .with(jwt().jwt(clientToken))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val revoked =
            objectMapper
                .readValue(
                    revokeResult.response.contentAsString,
                    ResourceShareResponse::class.java
                ).data
        assertThat(revoked.status).isEqualTo(ShareStatus.REVOKED)
    }

    @Test
    fun `cannot share resources with yourself`() {
        val request =
            ResourceShareInviteRequest(
                resourceType = ShareResourceType.INDEPENDENCE_PLAN,
                resourceIds = listOf("plan-self"),
                adviserEmail = "rs-client@testing.com"
            )

        mockMvc
            .perform(
                post("$RESOURCE_SHARES_ROOT/invite")
                    .with(jwt().jwt(clientToken))
                    .with(csrf())
                    .content(objectMapper.writeValueAsBytes(request))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().is4xxClientError)
    }

    @Test
    fun `check access returns true for active share`() {
        val inviteRequest =
            ResourceShareInviteRequest(
                resourceType = ShareResourceType.INDEPENDENCE_PLAN,
                resourceIds = listOf("plan-check"),
                adviserEmail = "rs-adviser@testing.com"
            )
        val inviteResult =
            mockMvc
                .perform(
                    post("$RESOURCE_SHARES_ROOT/invite")
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
                    ResourceSharesResponse::class.java
                ).data
                .first()
                .id

        // Before acceptance: no access
        val noAccessResult =
            mockMvc
                .perform(
                    get("$RESOURCE_SHARES_ROOT/check/INDEPENDENCE_PLAN/plan-check")
                        .with(jwt().jwt(adviserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val noAccess =
            objectMapper.readValue(
                noAccessResult.response.contentAsString,
                ShareAccessCheck::class.java
            )
        assertThat(noAccess.hasAccess).isFalse()

        // Accept
        mockMvc
            .perform(
                post("$RESOURCE_SHARES_ROOT/$shareId/accept")
                    .with(jwt().jwt(adviserToken))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        // After acceptance: has access
        val hasAccessResult =
            mockMvc
                .perform(
                    get("$RESOURCE_SHARES_ROOT/check/INDEPENDENCE_PLAN/plan-check")
                        .with(jwt().jwt(adviserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val hasAccess =
            objectMapper.readValue(
                hasAccessResult.response.contentAsString,
                ShareAccessCheck::class.java
            )
        assertThat(hasAccess.hasAccess).isTrue()
        assertThat(hasAccess.accessLevel).isEqualTo(ShareAccessLevel.VIEW)
    }

    @Test
    fun `managed resources returns active shares by type`() {
        val inviteRequest =
            ResourceShareInviteRequest(
                resourceType = ShareResourceType.REBALANCE_MODEL,
                resourceIds = listOf("model-managed"),
                adviserEmail = "rs-adviser@testing.com"
            )
        val inviteResult =
            mockMvc
                .perform(
                    post("$RESOURCE_SHARES_ROOT/invite")
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
                    ResourceSharesResponse::class.java
                ).data
                .first()
                .id

        mockMvc
            .perform(
                post("$RESOURCE_SHARES_ROOT/$shareId/accept")
                    .with(jwt().jwt(adviserToken))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        val managedResult =
            mockMvc
                .perform(
                    get("$RESOURCE_SHARES_ROOT/managed/REBALANCE_MODEL")
                        .with(jwt().jwt(adviserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val managed =
            objectMapper.readValue(
                managedResult.response.contentAsString,
                ResourceSharesResponse::class.java
            )
        assertThat(managed.data).isNotEmpty
        assertThat(managed.data.map { it.resourceId }).contains("model-managed")
    }

    @Test
    fun `email addresses are masked in pending notifications`() {
        val inviteRequest =
            ResourceShareInviteRequest(
                resourceType = ShareResourceType.INDEPENDENCE_PLAN,
                resourceIds = listOf("plan-mask"),
                adviserEmail = "rs-adviser@testing.com"
            )
        mockMvc
            .perform(
                post("$RESOURCE_SHARES_ROOT/invite")
                    .with(jwt().jwt(clientToken))
                    .with(csrf())
                    .content(objectMapper.writeValueAsBytes(inviteRequest))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(status().isOk)

        val pendingResult =
            mockMvc
                .perform(
                    get("$RESOURCE_SHARES_ROOT/pending")
                        .with(jwt().jwt(adviserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(status().isOk)
                .andReturn()

        val notifications =
            objectMapper.readValue(
                pendingResult.response.contentAsString,
                PendingResourceSharesResponse::class.java
            )
        val invite = notifications.invites.first()
        val ownerEmail = invite.resourceOwner.email
        assertThat(ownerEmail).contains("*")
        assertThat(ownerEmail).doesNotContain("rs-client@testing.com")
    }
}