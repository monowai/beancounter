package com.beancounter.agent.conversation

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * The conversations whose latest question is still being answered. Lets a
 * client that comes back to a conversation — a phone that slept mid-answer —
 * tell "still being answered, wait for it" from "orphaned, offer a retry".
 *
 * Process-local on purpose: bc-agent runs as a single replica, so the set is
 * complete. A restart loses in-flight turns, which then read as unanswered —
 * which they are, since the generation died with the process.
 */
@Component
class InFlightTurns {
    private val pending: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun begin(conversationId: String) {
        pending.add(conversationId)
    }

    fun end(conversationId: String) {
        pending.remove(conversationId)
    }

    fun isPending(conversationId: String): Boolean = conversationId in pending
}