package com.beancounter.agent.conversation

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface ConversationRepository : JpaRepository<Conversation, String> {
    fun findByIdAndOwnerId(
        id: String,
        ownerId: String
    ): Conversation?

    /**
     * The owner's conversation, row-locked until the transaction ends, so
     * concurrent appends take turn numbers one at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id and c.ownerId = :ownerId")
    fun lockOwned(
        id: String,
        ownerId: String
    ): Conversation?

    /** The owner's conversations that hold at least one turn, most recently active first. */
    @Query(
        "select c from Conversation c where c.ownerId = :ownerId " +
            "and exists (select 1 from ConversationMessage m where m.conversationId = c.id) " +
            "order by c.updatedAt desc"
    )
    fun findListed(
        ownerId: String,
        pageable: Pageable
    ): List<Conversation>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Conversation c where c.id = :id")
    fun deleteOne(id: String): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Conversation c where c.ownerId = :ownerId")
    fun deleteByOwner(ownerId: String): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Conversation c where c.updatedAt < :cutoff")
    fun deleteIdleSince(cutoff: Instant): Int

    /** Swap the title only while it is still [provisional] — a single statement, so a concurrent rename wins. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update Conversation c set c.title = :generated " +
            "where c.id = :id and c.ownerId = :ownerId and c.title = :provisional"
    )
    fun replaceTitle(
        id: String,
        ownerId: String,
        provisional: String,
        generated: String
    ): Int
}

interface ConversationMessageRepository : JpaRepository<ConversationMessage, String> {
    fun findByConversationIdOrderBySeq(conversationId: String): List<ConversationMessage>

    fun countByConversationId(conversationId: String): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ConversationMessage m where m.conversationId = :conversationId")
    fun deleteOfConversation(conversationId: String): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "delete from ConversationMessage m where m.conversationId in " +
            "(select c.id from Conversation c where c.ownerId = :ownerId)"
    )
    fun deleteOfOwner(ownerId: String): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "delete from ConversationMessage m where m.conversationId in " +
            "(select c.id from Conversation c where c.updatedAt < :cutoff)"
    )
    fun deleteOfIdleSince(cutoff: Instant): Int
}