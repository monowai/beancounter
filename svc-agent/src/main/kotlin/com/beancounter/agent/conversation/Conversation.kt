package com.beancounter.agent.conversation

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * One chat between a user and the agent. [ownerId] is the caller's
 * `SystemUser.id`. [updatedAt] moves with every turn, ordering the user's list
 * and driving the retention purge.
 */
@Entity
@Table(name = "conversation")
class Conversation(
    @Id
    val id: String = KeyGenUtils().id,
    @Column(nullable = false, length = 36)
    val ownerId: String,
    @Column(nullable = false, length = ConversationService.TITLE_MAX_CHARS)
    var title: String = "",
    @Column(nullable = false)
    val createdAt: Instant,
    @Column(nullable = false)
    var updatedAt: Instant
)

/**
 * One turn of a [Conversation]. An assistant turn that failed carries the
 * stable error code in [error] and no content, so the UI can render the same
 * copy after a reload that it showed live.
 */
@Entity
@Table(name = "conversation_message")
class ConversationMessage(
    @Id
    val id: String = KeyGenUtils().id,
    @Column(nullable = false, length = 36)
    val conversationId: String,
    @Column(nullable = false)
    val seq: Int,
    @Column(nullable = false, length = 16)
    val role: String,
    @Column(nullable = false, columnDefinition = "text")
    val content: String,
    @Column(nullable = false)
    val deepThink: Boolean = false,
    @Column(length = 64)
    val error: String? = null,
    /** Shown in place of [content] for a canned prompt, e.g. "News & Sentiment — NATO". */
    @Column(length = 200)
    val label: String? = null,
    @Column(nullable = false)
    val createdAt: Instant
)