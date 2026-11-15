package com.beancounter.agent.screenshot

import com.beancounter.agent.TurnMetering
import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.conversation.ConversationOwner
import com.beancounter.agent.quota.TurnQuota
import com.beancounter.auth.model.AuthConstants
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException

private const val OWNER = "owner-1"

/** Who may have a screenshot read, what is accepted, and when a read counts against the weekly quota. */
class ScreenshotControllerTest {
    private val reads = mutableListOf<Screenshot>()
    private var reply =
        """{"documentType":"HOLDINGS","brokerName":"Charles Schwab","rows":[{"code":"AAPL","quantity":42}]}"""
    private var modelFailure: RuntimeException? = null
    private val quota =
        object : TurnQuota {
            var left = 1
            var off = false
            val spent = mutableListOf<String>()

            override fun allows(ownerId: String) = left > 0

            override fun spend(ownerId: String) {
                spent += ownerId
            }

            override fun disabled(ownerId: String) = off
        }
    private val owner = mock<ConversationOwner> { on { id() } doReturn OWNER }
    private val authorizer = AgentScopeAuthorizer()
    private val controller =
        ScreenshotController(
            ScreenshotReader { _, screenshot ->
                reads += screenshot
                modelFailure?.let { throw it }
                reply
            },
            authorizer,
            TurnMetering(authorizer, owner, quota),
            quota,
            owner
        )

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun authenticateAs(vararg authorities: String) {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("test-user", "n/a", authorities.map(::SimpleGrantedAuthority))
    }

    private fun upload(
        contentType: String? = "image/png",
        bytes: ByteArray = byteArrayOf(1, 2, 3)
    ) = MockMultipartFile("file", "shot.png", contentType, bytes)

    private fun refusal(action: () -> Unit): HttpStatus {
        var status: HttpStatus? = null
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException::class.java) {
            status = HttpStatus.valueOf(it.statusCode.value())
        }
        return checkNotNull(status)
    }

    @Test
    fun `should read a metered caller's screenshot and count one turn`() {
        authenticateAs(AuthConstants.SCOPE_PREVIEW, AuthConstants.SCOPE_VERIFIED)

        val proposal = controller.read(upload("image/webp", byteArrayOf(7, 8)))

        assertThat(proposal.brokerName).isEqualTo("Charles Schwab")
        assertThat(proposal.rows.single().code).isEqualTo("AAPL")
        assertThat(reads.single().bytes).containsExactly(7, 8)
        assertThat(reads.single().mediaType).isEqualTo("image/webp")
        assertThat(quota.spent).containsExactly(OWNER)
    }

    @Test
    fun `should not count a read for a caller with no quota`() {
        authenticateAs(AuthConstants.SCOPE_AI)

        controller.read(upload())

        assertThat(reads).hasSize(1)
        assertThat(quota.spent).isEmpty()
    }

    @Test
    fun `should refuse a caller without an agent scope before reading anything`() {
        authenticateAs(AuthConstants.SCOPE_USER)

        assertThatThrownBy { controller.read(upload()) }.isInstanceOf(AccessDeniedException::class.java)
        assertThat(reads).isEmpty()
    }

    @Test
    fun `should refuse a metered caller who is unverified, switched off or out of turns`() {
        authenticateAs(AuthConstants.SCOPE_PREVIEW)
        assertThat(refusal { controller.read(upload()) }).isEqualTo(HttpStatus.FORBIDDEN)

        authenticateAs(AuthConstants.SCOPE_PREVIEW, AuthConstants.SCOPE_VERIFIED)
        quota.off = true
        assertThat(refusal { controller.read(upload()) }).isEqualTo(HttpStatus.FORBIDDEN)

        quota.off = false
        quota.left = 0
        assertThat(refusal { controller.read(upload()) }).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)

        assertThat(reads).isEmpty()
        assertThat(quota.spent).isEmpty()
    }

    @Test
    fun `should refuse a file that is empty, not an image or too large`() {
        authenticateAs(AuthConstants.SCOPE_AI)

        assertThat(refusal { controller.read(upload(bytes = ByteArray(0))) }).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(refusal { controller.read(upload("application/pdf")) }).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        assertThat(refusal { controller.read(upload(null)) }).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        assertThat(refusal { controller.read(upload(bytes = ByteArray(ScreenshotController.MAX_BYTES + 1))) })
            .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
        controller.read(upload(bytes = ByteArray(ScreenshotController.MAX_BYTES) { 1 }))

        assertThat(reads).hasSize(1)
    }

    @Test
    fun `should not count a screenshot the model could not read or did not answer`() {
        authenticateAs(AuthConstants.SCOPE_PREVIEW, AuthConstants.SCOPE_VERIFIED)

        reply = "no idea"
        assertThat(refusal { controller.read(upload()) }).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)

        modelFailure = VisionUnavailableException("down")
        assertThat(refusal { controller.read(upload()) }).isEqualTo(HttpStatus.BAD_GATEWAY)

        assertThat(quota.spent).isEmpty()
    }
}