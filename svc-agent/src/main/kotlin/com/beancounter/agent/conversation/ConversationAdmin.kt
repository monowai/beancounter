package com.beancounter.agent.conversation

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The owner's management of their conversations: start, list, read, rename
 * and delete them, and the retention purge. Every operation is scoped to an
 * owner's `SystemUser.id`; a conversation belonging to someone else is
 * reported as not found, so its existence is not disclosed. Writing turns is
 * [TurnLog]'s.
 */
@Service
@Transactional
class ConversationAdmin(
    private val conversations: ConversationRepository,
    private val messages: ConversationMessageRepository,
    private val inFlight: InFlightTurns,
    private val clock: Clock
) {
    fun create(
        ownerId: String,
        hidden: Boolean = false
    ): Conversation {
        val now = clock.instant()
        return conversations.save(Conversation(ownerId = ownerId, createdAt = now, updatedAt = now, hidden = hidden))
    }

    /**
     * The owner's conversation about [subject], started if there is none, so
     * one subject has one thread. `(owner_id, subject)` is unique: a racing
     * first request that loses the insert reads the winner's row instead.
     * Not transactional, so the lookup and the insert each commit on their
     * own and a lost insert poisons no surrounding transaction.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun findOrCreate(
        ownerId: String,
        subject: String
    ): Conversation =
        conversations.findByOwnerIdAndSubject(ownerId, subject)
            ?: try {
                val now = clock.instant()
                conversations.saveAndFlush(
                    Conversation(ownerId = ownerId, createdAt = now, updatedAt = now, subject = subject)
                )
            } catch (_: DataIntegrityViolationException) {
                conversations.findByOwnerIdAndSubject(ownerId, subject)
                    ?: throw IllegalStateException("Conversation for subject vanished after a duplicate insert")
            }

    @Transactional(readOnly = true)
    fun list(
        ownerId: String,
        page: Int,
        size: Int
    ): List<ConversationSummary> =
        conversations
            .findListed(ownerId, PageRequest.of(page, size))
            .map { ConversationSummary(it.id, it.title, it.createdAt, it.updatedAt, it.kind) }

    @Transactional(readOnly = true)
    fun get(
        ownerId: String,
        id: String
    ): ConversationDetail {
        val conversation = conversations.owned(ownerId, id)
        return ConversationDetail(
            id = conversation.id,
            title = conversation.title,
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt,
            messages =
                messages.findByConversationIdOrderBySeq(id).map {
                    ConversationTurn(it.id, it.role, it.content, it.createdAt, it.error, it.deepThink, it.label)
                },
            pending = inFlight.isPending(id),
            kind = conversation.kind
        )
    }

    fun rename(
        ownerId: String,
        id: String,
        title: String
    ) {
        require(title.isNotBlank()) { "A conversation title cannot be blank" }
        conversations.owned(ownerId, id).title = ConversationTitles.clip(title)
    }

    fun delete(
        ownerId: String,
        id: String
    ) {
        conversations.owned(ownerId, id)
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
}

data class ConversationSummary(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val kind: ConversationKind = ConversationKind.CHAT
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
    val pending: Boolean,
    val kind: ConversationKind = ConversationKind.CHAT
)