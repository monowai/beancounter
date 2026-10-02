package com.beancounter.agent.conversation

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

/**
 * Asks the fast, non-thinking model for a short noun-phrase title once a
 * conversation has its first answer, off the request path. The provisional
 * title from [ConversationTitles] stays when there is no model or the call
 * fails, and a title the user chose is never overwritten.
 */
@Component
class ConversationTitler(
    @Autowired(required = false)
    @Qualifier("fastChatClient")
    private val fastChatClient: ChatClient?,
    @Autowired(required = false) private val chatClient: ChatClient?,
    private val service: ConversationService,
    @Qualifier("conversationTitleScope") private val scope: CoroutineScope
) {
    private val log = LoggerFactory.getLogger(ConversationTitler::class.java)

    private companion object {
        const val ANSWER_CHARS = 600
        const val SYSTEM =
            "You title chat conversations. Reply with the title only: 2 to 5 words, the key nouns, " +
                "Title Case, no quotes, no trailing punctuation."
    }

    /** Title [conversationId] from its [questions] and first [answer]; no-op without a model. */
    fun suggest(
        ownerId: String,
        conversationId: String,
        provisional: String,
        questions: List<String>,
        answer: String
    ): Job? {
        val client = fastChatClient ?: chatClient ?: return null
        return scope.launch {
            try {
                val title =
                    clean(
                        client
                            .prompt()
                            .system(SYSTEM)
                            .user(prompt(questions, answer))
                            .call()
                            .content()
                    )
                if (title != null) service.applyGeneratedTitle(ownerId, conversationId, provisional, title)
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception
            ) {
                log.warn("Conversation title generation failed, keeping provisional title: {}", e.message)
            }
        }
    }

    private fun prompt(
        questions: List<String>,
        answer: String
    ): String = questions.joinToString("\n") { "Question: $it" } + "\nAnswer: ${answer.take(ANSWER_CHARS)}"

    private fun clean(raw: String?): String? =
        raw
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.trim('"', '\'', '*', '`')
            ?.trimEnd('.', '!', '?', ':')
            ?.trim()
            ?.take(ConversationService.TITLE_MAX_CHARS)
            ?.takeIf { it.isNotBlank() }
}

@Configuration
class ConversationTitleScopeConfig {
    /** A failed title must not cancel others; titles are a few small calls a day. */
    @Bean("conversationTitleScope")
    fun conversationTitleScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("conversation-title"))
}