package com.beancounter.agent

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Configuration for ChatClient beans to resolve multiple ChatModel conflicts.
 *
 * When both Ollama and OpenAI are on the classpath, Spring AI cannot auto-create
 * a ChatClient because it doesn't know which ChatModel to use. This configuration
 * provides profile-specific ChatClient beans to resolve the conflict.
 */
@Configuration
class ChatClientConfiguration {
    private val log = LoggerFactory.getLogger(ChatClientConfiguration::class.java)

    /**
     * ChatClient for Ollama profile
     * Only created when Ollama profile is active and OllamaChatModel is available
     */
    @Bean("chatClient")
    @Profile("ollama")
    fun ollamaChatClient(chatModel: OllamaChatModel): ChatClient {
        log.info("Creating ChatClient for Ollama profile with model: {}", chatModel.javaClass.simpleName)
        return ChatClient.builder(chatModel).build()
    }

    /**
     * ChatClient for OpenAI profile
     * Only created when OpenAI profile is active and OpenAiChatModel is available
     */
    @Bean("chatClient")
    @Profile("openai")
    fun openAiChatClient(chatModel: OpenAiChatModel): ChatClient {
        log.info("Creating ChatClient for OpenAI profile with model: {}", chatModel.javaClass.simpleName)
        return ChatClient.builder(chatModel).build()
    }
}