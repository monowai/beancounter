package com.beancounter.agent.conversation

import com.beancounter.agent.ChatTurn
import com.beancounter.agent.conversation.Utterance.Companion.ROLE_ASSISTANT
import com.beancounter.agent.conversation.Utterance.Companion.ROLE_USER
import com.beancounter.common.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * The turn path: what answering a question reads and writes in its
 * conversation. Every operation is scoped to an owner's `SystemUser.id`; a
 * conversation belonging to someone else is reported as not found. Listing,
 * renaming and deleting conversations is [ConversationAdmin]'s.
 */
@Service
@Transactional
class TurnLog(
    private val conversations: ConversationRepository,
    private val messages: ConversationMessageRepository,
    private val clock: Clock
) {
    /** What the owner's conversation [id] is, and whether it holds a turn yet. */
    @Transactional(readOnly = true)
    fun stateOf(
        ownerId: String,
        id: String
    ): ConversationState = ConversationState(conversations.owned(ownerId, id).kind, messages.existsByConversationId(id))

    /**
     * Record a question. The first one also fixes the conversation's [kind]:
     * the locked row is dirty-checked, so the kind commits with this turn or
     * not at all.
     */
    fun appendUser(
        ownerId: String,
        id: String,
        question: Question,
        kind: ConversationKind = ConversationKind.CHAT
    ): ConversationMessage = ask(locked(ownerId, id), question, kind)

    /**
     * Open a turn in one transaction: the replayable [history] before it,
     * [question] recorded, and — while the conversation has no answer and is
     * listed — the seed its first answer is titled from.
     */
    fun openTurn(
        ownerId: String,
        id: String,
        question: Question,
        kind: ConversationKind,
        maxHistory: Int
    ): OpenedTurn {
        val conversation = locked(ownerId, id)
        val stored = replayable(id, maxHistory)
        ask(conversation, question, kind)
        // A hidden conversation is never listed, so a title would be an LLM call for nothing.
        val awaitingTitle = stored.none { it.role == ROLE_ASSISTANT } && !conversation.hidden
        val seed =
            TitleSeed(
                conversation.title,
                stored.filter { it.role == ROLE_USER }.map { it.content } + question.subject
            )
        return OpenedTurn(stored, seed.takeIf { awaitingTitle })
    }

    fun appendAssistant(
        ownerId: String,
        id: String,
        content: String,
        error: String?
    ): ConversationMessage = append(locked(ownerId, id), Utterance(ROLE_ASSISTANT, content, error = error))

    @Transactional(readOnly = true)
    fun titleOf(
        ownerId: String,
        id: String
    ): String = conversations.owned(ownerId, id).title

    /**
     * Swap in a model-written [generated] title, but only while the title is
     * still the [provisional] one — a rename by the user wins.
     */
    fun applyGeneratedTitle(
        ownerId: String,
        id: String,
        provisional: String,
        generated: String
    ): Boolean = conversations.replaceTitle(id, ownerId, provisional, ConversationTitles.clip(generated)) == 1

    private fun ask(
        conversation: Conversation,
        question: Question,
        kind: ConversationKind
    ): ConversationMessage {
        if (conversation.title.isBlank()) conversation.title = ConversationTitles.provisional(question.subject)
        if (!messages.existsByConversationId(conversation.id)) conversation.kind = kind
        return append(conversation, Utterance.asked(question))
    }

    /**
     * Prior turns to replay to the model, oldest first: the trailing [maxTurns]
     * that the model actually said or was asked. Failed turns are left out —
     * the model never said that text.
     */
    private fun replayable(
        id: String,
        maxTurns: Int
    ): List<ChatTurn> =
        messages
            .findByConversationIdOrderBySeq(id)
            .filter { it.error == null && it.content.isNotEmpty() }
            .takeLast(maxTurns)
            .map { ChatTurn(it.role, it.content) }

    /** The owner's conversation, row-locked so appends to one conversation number their turns in turn. */
    private fun locked(
        ownerId: String,
        id: String
    ): Conversation =
        conversations.lockOwned(id, ownerId)
            ?: throw NotFoundException("Conversation not found: $id")

    private fun append(
        conversation: Conversation,
        utterance: Utterance
    ): ConversationMessage {
        val now = clock.instant()
        conversation.updatedAt = now
        return messages.save(
            ConversationMessage(
                conversationId = conversation.id,
                seq = messages.countByConversationId(conversation.id),
                role = utterance.role,
                content = utterance.content,
                deepThink = utterance.deepThink,
                error = utterance.error,
                label = utterance.label,
                createdAt = now
            )
        )
    }
}

/** What a conversation is and whether it has begun. */
data class ConversationState(
    val kind: ConversationKind,
    val hasTurns: Boolean
)