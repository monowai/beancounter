package com.beancounter.marketdata.share

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.PortfolioShare
import com.beancounter.common.model.ResourceShare
import com.beancounter.common.model.ShareResourceType
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * The share rules every kind of share follows: who may accept, who may revoke, and when an
 * invite is refused.
 */
internal class ShareLifecycleTest {
    private val owner = SystemUser(id = "owner", email = "owner@testing.com")
    private val adviser = SystemUser(id = "adviser", email = "adviser@testing.com")
    private val outsider = SystemUser(id = "outsider", email = "outsider@testing.com")

    private val repository =
        mock<ResourceShareRepository> {
            on { save(any<ResourceShare>()) } doAnswer { it.getArgument(0) }
        }
    private val systemUserService = mock<SystemUserService>()
    private val lifecycle = ShareLifecycle(repository, systemUserService)

    @Test
    fun `should resolve the counterparty by email`() {
        whenever(systemUserService.findByEmail(adviser.email)).thenReturn(adviser)

        assertThat(lifecycle.counterparty(adviser.email, owner, SELF)).isEqualTo(adviser)
    }

    @Test
    fun `should refuse an unknown counterparty without disclosing the email`() {
        assertThatThrownBy { lifecycle.counterparty("nobody@testing.com", owner, SELF) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("User not found: n****y@testing.com")
    }

    @Test
    fun `should refuse sharing with yourself`() {
        whenever(systemUserService.findByEmail(owner.email)).thenReturn(owner)

        assertThatThrownBy { lifecycle.counterparty(owner.email, owner, SELF) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage(SELF)
    }

    @Test
    fun `should refuse a new invite while one is active or pending`() {
        listOf(ShareStatus.ACTIVE, ShareStatus.PENDING_CLIENT_INVITE).forEach { status ->
            assertThatThrownBy { lifecycle.requireNotShared(Optional.of(invite(status)), ALREADY) }
                .describedAs(status.name)
                .isInstanceOf(BusinessException::class.java)
                .hasMessage(ALREADY)
        }
    }

    @Test
    fun `should allow a new invite once the last one was revoked`() {
        lifecycle.requireNotShared(Optional.of(invite(ShareStatus.REVOKED)), ALREADY)
        lifecycle.requireNotShared(Optional.empty(), ALREADY)
    }

    @Test
    fun `should accept an invite for the invited adviser`() {
        val share = stored(invite(ShareStatus.PENDING_CLIENT_INVITE))
        whenever(systemUserService.getOrThrow()).thenReturn(adviser)

        val accepted = lifecycle.accept(share.id)

        assertThat(accepted.status).isEqualTo(ShareStatus.ACTIVE)
        assertThat(accepted.acceptedAt).isNotNull()
    }

    @Test
    fun `should not let the owner accept their own invite`() {
        val share = stored(invite(ShareStatus.PENDING_CLIENT_INVITE))
        whenever(systemUserService.getOrThrow()).thenReturn(owner)

        assertThatThrownBy { lifecycle.accept(share.id) }.isInstanceOf(NotFoundException::class.java)
        assertThat(share.status).isEqualTo(ShareStatus.PENDING_CLIENT_INVITE)
    }

    @Test
    fun `should not accept a share that is no longer pending`() {
        val share = stored(invite(ShareStatus.ACTIVE))
        whenever(systemUserService.getOrThrow()).thenReturn(adviser)

        assertThatThrownBy { lifecycle.accept(share.id) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("Share is not pending: ${share.id}")
    }

    @Test
    fun `should report an unknown share as not found`() {
        whenever(repository.findById("missing")).thenReturn(Optional.empty())
        whenever(systemUserService.getOrThrow()).thenReturn(adviser)

        assertThatThrownBy { lifecycle.accept("missing") }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("Share not found: missing")
    }

    @Test
    fun `should let the owner and the adviser revoke`() {
        listOf(owner, adviser).forEach { party ->
            val share = stored(invite(ShareStatus.ACTIVE))
            whenever(systemUserService.getOrThrow()).thenReturn(party)

            assertThat(lifecycle.revoke(share.id).status).describedAs(party.id).isEqualTo(ShareStatus.REVOKED)
        }
    }

    @Test
    fun `should not let an outsider revoke`() {
        val share = stored(invite(ShareStatus.ACTIVE))
        whenever(systemUserService.getOrThrow()).thenReturn(outsider)

        assertThatThrownBy { lifecycle.revoke(share.id) }.isInstanceOf(NotFoundException::class.java)
        assertThat(share.status).isEqualTo(ShareStatus.ACTIVE)
    }

    @Test
    fun `should add no owner field to a share's JSON`() {
        val request = PortfolioShare(sharedWith = adviser, createdBy = adviser, targetUser = owner)

        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(invite(ShareStatus.ACTIVE))).has("owner"))
            .isFalse()
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(request)).has("owner")).isFalse()
    }

    private fun invite(status: ShareStatus) =
        ResourceShare(
            resourceType = ShareResourceType.INDEPENDENCE_PLAN,
            resourceId = "plan",
            sharedWith = adviser,
            status = status,
            createdBy = owner,
            resourceOwner = owner
        )

    private fun stored(share: ResourceShare): ResourceShare {
        whenever(repository.findById(share.id)).thenReturn(Optional.of(share))
        return share
    }

    private companion object {
        const val SELF = "Cannot share with yourself"
        const val ALREADY = "Already shared"
    }
}