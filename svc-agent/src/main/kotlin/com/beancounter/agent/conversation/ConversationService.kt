package com.beancounter.agent.conversation

import com.beancounter.agent.ChatTurn
import com.beancounter.common.exception.NotFoundException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Persists agent conversations. Every operation is scoped to an owner's
 * `SystemUser.id`; a conversation belonging to someone else is reported as not
 * found, so its existence is not disclosed.
 */
@Service
@Transactional
class ConversationService(
    private val conversations: ConversationRepository,
    private val messages: ConversationMessageRepository,
    private val clock: Clock
) {
    companion object {
        const val TITLE_MAX_CHARS = 60
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        private val WHITESPACE = Regex("\\s+")
    }

    fun create(ownerId: String): Conversation {
        val now = clock.instant()
        return conversations.save(Conversation(ownerId = ownerId, createdAt = now, updatedAt = now))
    }

    @Transactional(readOnly = true)
    fun list(
        ownerId: String,
        page: Int,
        size: Int
    ): List<ConversationSummary> =
        conversations
            .findListed(ownerId, PageRequest.of(page, size))
            .map { ConversationSummary(it.id, it.title, it.createdAt, it.updatedAt) }

    @Transactional(readOnly = true)
    fun get(
        ownerId: String,
        id: String
    ): ConversationDetail {
        val conversation = owned(ownerId, id)
        return ConversationDetail(
            id = conversation.id,
            title = conversation.title,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            messages =
                messages.findByConversationIdOrderBySeq(id).map {
                    ConversationTurn(it.id, it.role, it.content, it.createdAt, it.error, it.deepThink)
                }
        )
    }

    fun appendUser(
        ownerId: String,
        id: String,
        content: String,
        deepThink: Boolean
    ): ConversationMessage {
        val conversation = owned(ownerId, id)
        if (conversation.title.isBlank()) conversation.title = titleFrom(content)
        return append(conversation, ROLE_USER, content, deepThink, null)
    }

    fun appendAssistant(
        ownerId: String,
        id: String,
        content: String,
        error: String?
    ): ConversationMessage = append(owned(ownerId, id), ROLE_ASSISTANT, content, false, error)

    /**
     * Prior turns to replay to the model, oldest first: the trailing [maxTurns]
     * that the model actually said or was asked. Failed turns are left out —
     * the model never said that text.
     */
    @Transactional(readOnly = true)
    fun history(
        ownerId: String,
        id: String,
        maxTurns: Int
    ): List<ChatTurn> {
        owned(ownerId, id)
        return messages
            .findByConversationIdOrderBySeq(id)
            .filter { it.error == null && it.content.isNotEmpty() }
            .takeLast(maxTurns)
            .map { ChatTurn(it.role, it.content) }
    }

    fun rename(
        ownerId: String,
        id: String,
        title: String
    ) {
        owned(ownerId, id).title = title.trim().take(TITLE_MAX_CHARS)
    }

    fun delete(
        ownerId: String,
        id: String
    ) {
        deleteIds(listOf(owned(ownerId, id).id))
    }

    /** Remove every conversation [ownerId] has — the offboarding path. */
    fun deleteAll(ownerId: String): Int = deleteIds(conversations.findIdsByOwnerId(ownerId))

    /** Remove conversations with no turn in the last [idle]. */
    fun purgeIdleLongerThan(idle: Duration): Int =
        deleteIds(conversations.findIdsIdleSince(clock.instant().minus(idle)))

    private fun deleteIds(ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        messages.deleteByConversationIds(ids)
        return conversations.deleteByIds(ids)
    }

    private fun owned(
        ownerId: String,
        id: String
    ): Conversation =
        conversations.findByIdAndOwnerId(id, ownerId)
            ?: throw NotFoundException("Conversation not found: $id")

    private fun append(
        conversation: Conversation,
        role: String,
        content: String,
        deepThink: Boolean,
        error: String?
    ): ConversationMessage {
        val now = clock.instant()
        conversation.updatedAt = now
        return messages.save(
            ConversationMessage(
                conversationId = conversation.id,
                seq = messages.countByConversationId(conversation.id),
                role = role,
                content = content,
                deepThink = deepThink,
                error = error,
                createdAt = now
            )
        )
    }

    private fun titleFrom(content: String): String {
        val flat = content.trim().replace(WHITESPACE, " ")
        return if (flat.length <= TITLE_MAX_CHARS) flat else flat.take(TITLE_MAX_CHARS - 1).trimEnd() + "…"
    }
}

data class ConversationSummary(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class ConversationTurn(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Instant,
    val error: String?,
    val deepThink: Boolean
)

data class ConversationDetail(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messages: List<ConversationTurn>
)