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
    private val inFlight: InFlightTurns,
    private val clock: Clock
) {
    companion object {
        const val TITLE_MAX_CHARS = 60
        const val LABEL_MAX_CHARS = 200
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
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
                    ConversationTurn(it.id, it.role, it.content, it.createdAt, it.error, it.deepThink, it.label)
                },
            pending = inFlight.isPending(id)
        )
    }

    fun appendUser(
        ownerId: String,
        id: String,
        content: String,
        deepThink: Boolean,
        label: String? = null
    ): ConversationMessage {
        val conversation = locked(ownerId, id)
        if (conversation.title.isBlank()) conversation.title = ConversationTitles.provisional(label ?: content)
        // Caller-supplied display text: clip to the column rather than fail the turn.
        return append(conversation, ROLE_USER, content, deepThink, null, label?.take(LABEL_MAX_CHARS))
    }

    fun appendAssistant(
        ownerId: String,
        id: String,
        content: String,
        error: String?
    ): ConversationMessage = append(locked(ownerId, id), ROLE_ASSISTANT, content, false, error, null)

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

    @Transactional(readOnly = true)
    fun titleOf(
        ownerId: String,
        id: String
    ): String = owned(ownerId, id).title

    /**
     * Swap in a model-written [generated] title, but only while the title is
     * still the [provisional] one — a rename by the user wins.
     */
    fun applyGeneratedTitle(
        ownerId: String,
        id: String,
        provisional: String,
        generated: String
    ): Boolean = conversations.replaceTitle(id, ownerId, provisional, clip(generated)) == 1

    fun rename(
        ownerId: String,
        id: String,
        title: String
    ) {
        require(title.isNotBlank()) { "A conversation title cannot be blank" }
        owned(ownerId, id).title = clip(title)
    }

    fun delete(
        ownerId: String,
        id: String
    ) {
        owned(ownerId, id)
        messages.deleteOfConversation(id)
        conversations.deleteOne(id)
    }

    /** Remove every conversation [ownerId] has — the offboarding path. */
    fun deleteAll(ownerId: String): Int {
        messages.deleteOfOwner(ownerId)
        return conversations.deleteByOwner(ownerId)
    }

    /** Remove conversations with no turn in the last [idle]. */
    fun purgeIdleLongerThan(idle: Duration): Int {
        val cutoff = clock.instant().minus(idle)
        messages.deleteOfIdleSince(cutoff)
        return conversations.deleteIdleSince(cutoff)
    }

    private fun clip(title: String): String = title.trim().take(TITLE_MAX_CHARS).trimEnd()

    private fun owned(
        ownerId: String,
        id: String
    ): Conversation =
        conversations.findByIdAndOwnerId(id, ownerId)
            ?: throw NotFoundException("Conversation not found: $id")

    /** [owned], row-locked so appends to one conversation number their turns in turn. */
    private fun locked(
        ownerId: String,
        id: String
    ): Conversation =
        conversations.lockOwned(id, ownerId)
            ?: throw NotFoundException("Conversation not found: $id")

    private fun append(
        conversation: Conversation,
        role: String,
        content: String,
        deepThink: Boolean,
        error: String?,
        label: String?
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
                label = label,
                createdAt = now
            )
        )
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
    val deepThink: Boolean,
    val label: String?
)

data class ConversationDetail(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messages: List<ConversationTurn>,
    /**
     * True while the latest question is still being answered — see [InFlightTurns].
     *
     * Read separately from [messages] (a DB query vs the in-memory registry), so a
     * reader can transiently see `pending == true` with the answer already present
     * in [messages]. The flag clears only after the answer row is written, never
     * before, so the reverse — `pending == false` with the answer still being
     * written — cannot happen. A best-effort hint: a client that sees it polls again.
     */
    val pending: Boolean
)