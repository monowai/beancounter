package com.beancounter.agent.conversation

/**
 * A question asked in a conversation: its text, whether it escalated to the
 * deep tier, and the display [label] a canned prompt is shown under instead.
 */
data class Question(
    val content: String,
    val deepThink: Boolean = false,
    val label: String? = null
) {
    /** What a title is written from: the label, which names the subject without a canned prompt's instructions. */
    val subject: String
        get() = label ?: content
}

/**
 * What a conversation's generated title is written from: the [provisional]
 * title it replaces (a rename by the user wins over it), and the
 * [questions] asked so far.
 */
data class TitleSeed(
    val provisional: String,
    val questions: List<String>
)

/**
 * A question just recorded: the turns before it, to replay to the model, and
 * the [titleSeed] its answer is titled from — null when the conversation
 * already has an answer, or is hidden and never listed.
 */
data class OpenedTurn(
    val history: List<com.beancounter.agent.ChatTurn>,
    val titleSeed: TitleSeed?
)