package com.beancounter.agent

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

/**
 * Configuration for the Beancounter AI Agent
 *
 * Sets up the necessary beans for agent functionality including:
 * - RestClient-based MCP server communication
 * - LLM service for natural language processing
 * - Agent orchestration
 */
@Configuration
class AgentConfiguration {
    /**
     * LLM Service bean for natural language processing
     */
    @Bean
    fun llmService(): LlmService = SimpleLlmService()

    /**
     * RestTemplate bean for HTTP client operations
     */
    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}