package com.beancounter.agent.conversation

/**
 * One side of a turn as it is appended to a conversation: who spoke
 * ([role]) and what they said. A question also carries whether it went to the
 * deep tier and the display [label] a canned prompt is shown under; an answer
 * that failed carries its stable [error] code and no content.
 */
data class Utterance(
    val role: String,
    val content: String,
    val deepThink: Boolean = false,
    val error: String? = null,
    val label: String? = null
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val LABEL_MAX_CHARS = 200

        /**
         * [question] as the user's side. Its label is caller-supplied display
         * text: clipped to the column rather than fail the turn.
         */
        fun asked(question: Question): Utterance =
            Utterance(ROLE_USER, question.content, question.deepThink, label = question.label?.take(LABEL_MAX_CHARS))
    }
}