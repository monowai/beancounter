package com.beancounter.marketdata.share

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Share
import com.beancounter.common.model.ShareStatus
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.registration.SystemUserService
import org.springframework.data.repository.CrudRepository
import java.time.Instant
import java.util.Optional

/**
 * The lifecycle every [Share] follows, whatever is shared: find the other party, refuse a second
 * live invite, accept a pending invite or request, and revoke. A share the caller is no party to
 * reads as not found, so its existence is not disclosed. Runs in the calling service's
 * transaction.
 */
class ShareLifecycle<S : Share>(
    private val repository: CrudRepository<S, String>,
    private val systemUserService: SystemUserService
) {
    /** The user at [email], who must not be [caller]: nobody shares with themselves. */
    fun counterparty(
        email: String,
        caller: SystemUser,
        selfShareMessage: String
    ): SystemUser {
        val user =
            systemUserService.findByEmail(email)
                ?: throw NotFoundException("User not found: ${maskEmail(email)}")
        if (user.id == caller.id) {
            throw BusinessException(selfShareMessage)
        }
        return user
    }

    /** Refuse a new invite while [existing] is still active or pending. */
    fun requireNotShared(
        existing: Optional<S>,
        message: String
    ) {
        val status = existing.map { it.status }.orElse(null)
        if (status == ShareStatus.ACTIVE || status == ShareStatus.PENDING_CLIENT_INVITE) {
            throw BusinessException(message)
        }
    }

    /**
     * Accept a pending share. Only the invited adviser accepts an owner's invite, and only the
     * targeted client accepts an adviser's request.
     */
    fun accept(shareId: String): S {
        val caller = systemUserService.getOrThrow()
        val share = find(shareId)
        if (!canAccept(share, caller, shareId)) {
            throw NotFoundException(shareNotFoundMessage(shareId))
        }
        share.status = ShareStatus.ACTIVE
        share.acceptedAt = Instant.now()
        return repository.save(share)
    }

    /** Revoke a share. Any party to it can: the owner, the adviser, or the targeted client. */
    fun revoke(shareId: String): S {
        val caller = systemUserService.getOrThrow()
        val share = find(shareId)
        val isParty =
            share.owner()?.id == caller.id ||
                share.sharedWith.id == caller.id ||
                share.targetUser?.id == caller.id
        if (!isParty) {
            throw NotFoundException(shareNotFoundMessage(shareId))
        }
        share.status = ShareStatus.REVOKED
        return repository.save(share)
    }

    private fun find(shareId: String): S =
        repository.findById(shareId).orElseThrow {
            NotFoundException(shareNotFoundMessage(shareId))
        }

    private fun canAccept(
        share: S,
        caller: SystemUser,
        shareId: String
    ): Boolean =
        when (share.status) {
            ShareStatus.PENDING_CLIENT_INVITE -> {
                share.sharedWith.id == caller.id
            }
            ShareStatus.PENDING_ADVISER_REQUEST -> {
                val target =
                    share.targetUser
                        ?: throw BusinessException("Share $shareId has no target user")
                target.id == caller.id
            }
            else -> {
                throw BusinessException("Share is not pending: $shareId")
            }
        }

    private fun shareNotFoundMessage(shareId: String): String = "Share not found: $shareId"
}

/** [user] with a masked email unless they are [caller]: a share shows the caller only their own. */
fun maskUnlessCaller(
    user: SystemUser,
    caller: SystemUser
): SystemUser = if (user.id == caller.id) user else user.copy(email = maskEmail(user.email))

/**
 * Mask an email address: m***h@gmail.com
 */
fun maskEmail(email: String): String {
    val atIndex = email.indexOf('@')
    if (atIndex <= 1) return email
    val local = email.substring(0, atIndex)
    val domain = email.substring(atIndex)
    return if (local.length <= 2) {
        "${local.first()}*$domain"
    } else {
        "${local.first()}${"*".repeat(local.length - 2)}${local.last()}$domain"
    }
}