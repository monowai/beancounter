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

/** Records into [conversationId], owned by [ownerId]. */
class ConversationRecorder(
    private val service: ConversationService,
    private val ownerId: String,
    private val conversationId: String
) : TurnRecorder {
    override fun answered(content: String) {
        service.appendAssistant(ownerId, conversationId, content, null)
    }

    override fun failed(code: String) {
        service.appendAssistant(ownerId, conversationId, "", code)
    }
}