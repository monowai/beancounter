package com.beancounter.agent.conversation

/**
 * What a conversation is, fixed by its first turn. An Asset Review is about
 * the asset alone; anything about the caller's own holding of it is a
 * Holding Analysis, a conversation of its own.
 */
enum class ConversationKind {
    CHAT,
    ASSET_REVIEW,
    HOLDING_ANALYSIS
}