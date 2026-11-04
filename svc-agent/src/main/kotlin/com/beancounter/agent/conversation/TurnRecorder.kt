package com.beancounter.agent.conversation

/** Records the assistant's side of one agent turn. */
interface TurnRecorder {
    fun answered(content: String)

    fun failed(code: String)

    companion object {
        /** A stateless request: nothing is kept. */
        val NONE =
            object : TurnRecorder {
                override fun answered(content: String) = Unit

                override fun failed(code: String) = Unit
            }
    }
}

/**
 * Records into [conversationId], owned by [ownerId]. With a [titleSeed] (the
 * conversation has no answer yet), its first answer also asks [titler] to
 * replace the provisional title.
 */
class ConversationRecorder(
    private val turns: TurnLog,
    private val titler: ConversationTitler,
    private val ownerId: String,
    private val conversationId: String,
    private val titleSeed: TitleSeed?
) : TurnRecorder {
    override fun answered(content: String) {
        turns.appendAssistant(ownerId, conversationId, content, null)
        titleSeed?.let { titler.suggest(ownerId, conversationId, it, content) }
    }

    override fun failed(code: String) {
        turns.appendAssistant(ownerId, conversationId, "", code)
    }
}