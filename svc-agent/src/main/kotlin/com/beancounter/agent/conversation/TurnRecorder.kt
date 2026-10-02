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
 * Records into [conversationId], owned by [ownerId]. While the conversation
 * has no answer yet ([awaitingTitle]), its first answer also asks
 * [titler] to replace the [provisionalTitle], given the [questions] so far.
 */
class ConversationRecorder(
    private val service: ConversationService,
    private val titler: ConversationTitler,
    private val ownerId: String,
    private val conversationId: String,
    private val awaitingTitle: Boolean,
    private val provisionalTitle: String,
    private val questions: List<String>
) : TurnRecorder {
    override fun answered(content: String) {
        service.appendAssistant(ownerId, conversationId, content, null)
        if (awaitingTitle) titler.suggest(ownerId, conversationId, provisionalTitle, questions, content)
    }

    override fun failed(code: String) {
        service.appendAssistant(ownerId, conversationId, "", code)
    }
}