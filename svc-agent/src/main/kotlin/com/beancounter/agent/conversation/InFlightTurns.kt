package com.beancounter.agent.conversation

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * The conversations whose latest question is still being answered. Lets a
 * client that comes back to a conversation — a phone that slept mid-answer —
 * tell "still being answered, wait for it" from "orphaned, offer a retry".
 *
 * Counted, not a set: two turns can overlap on one conversation (a retry while
 * the first is still generating, a second device), and the flag must hold until
 * the last of them ends rather than clear when the first does.
 *
 * Process-local on purpose: bc-agent runs as a single replica, so the registry
 * is complete. A restart loses in-flight turns, which then read as unanswered —
 * which they are, since the generation died with the process.
 */
@Component
class InFlightTurns {
    private val pending = ConcurrentHashMap<String, Int>()

    fun begin(conversationId: String) {
        pending.merge(conversationId, 1, Int::plus)
    }

    fun end(conversationId: String) {
        pending.compute(conversationId) { _, n -> if (n == null || n <= 1) null else n - 1 }
    }

    fun isPending(conversationId: String): Boolean = pending.containsKey(conversationId)
}