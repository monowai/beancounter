package com.beancounter.agent.conversation

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface ConversationRepository : JpaRepository<Conversation, String> {
    fun findByIdAndOwnerId(
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

    @Query("select c.id from Conversation c where c.ownerId = :ownerId")
    fun findIdsByOwnerId(ownerId: String): List<String>

    @Query("select c.id from Conversation c where c.updatedAt < :cutoff")
    fun findIdsIdleSince(cutoff: Instant): List<String>

    @Modifying
    @Query("delete from Conversation c where c.id in :ids")
    fun deleteByIds(ids: Collection<String>): Int
}

interface ConversationMessageRepository : JpaRepository<ConversationMessage, String> {
    fun findByConversationIdOrderBySeq(conversationId: String): List<ConversationMessage>

    fun countByConversationId(conversationId: String): Int

    @Modifying
    @Query("delete from ConversationMessage m where m.conversationId in :ids")
    fun deleteByConversationIds(ids: Collection<String>): Int
}