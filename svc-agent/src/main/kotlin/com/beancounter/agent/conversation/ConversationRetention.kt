package com.beancounter.agent.conversation

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Deletes conversations with no turn in the last `agent.conversations.retention-days`.
 * Conversations carry holdings and amounts; they are not kept indefinitely.
 */
@Component
class ConversationRetention(
    private val service: ConversationService,
    @Value($$"${agent.conversations.retention-days}") private val retentionDays: Long
) {
    private val log = LoggerFactory.getLogger(ConversationRetention::class.java)

    @Scheduled(cron = $$"${agent.conversations.purge-cron}")
    fun purge() {
        val purged = service.purgeIdleLongerThan(Duration.ofDays(retentionDays))
        log.info("Purged {} conversations idle longer than {} days", purged, retentionDays)
    }
}

@Configuration
@EnableScheduling
class ConversationScheduleConfig