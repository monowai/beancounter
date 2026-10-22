package com.beancounter.agent.quota

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface AgentUsageRepository : JpaRepository<AgentUsage, String> {
    /** The owner's turns answered after [since], oldest first. */
    fun findByOwnerIdAndUsedAtAfterOrderByUsedAt(
        ownerId: String,
        since: Instant
    ): List<AgentUsage>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AgentUsage u where u.ownerId = :ownerId and u.usedAt <= :cutoff")
    fun deleteOfOwnerUpTo(
        ownerId: String,
        cutoff: Instant
    ): Int

    /** Every owner with a turn answered after [since]. */
    @Query("select distinct u.ownerId from AgentUsage u where u.usedAt > :since")
    fun ownersActiveAfter(since: Instant): List<String>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AgentUsage u where u.ownerId = :ownerId")
    fun deleteOfOwner(ownerId: String): Int
}

interface AgentQuotaLimitRepository : JpaRepository<AgentQuotaLimit, String>