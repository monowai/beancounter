package com.beancounter.agent

import com.beancounter.agent.config.FeignAuthInterceptor
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

/**
 * Configuration for the Beancounter AI Agent
 *
 * Sets up the necessary beans for agent functionality including:
 * - Feign clients for MCP server communication
 * - LLM service for natural language processing
 * - Agent orchestration
 */
@Configuration
@EnableFeignClients(basePackages = ["com.beancounter.agent.client"])
class AgentConfiguration {
    /**
     * LLM Service bean for natural language processing
     */
    @Bean
    fun llmService(): LlmService = SimpleLlmService()

    /**
     * Feign Auth Interceptor bean for forwarding JWT tokens
     */
    @Bean
    fun feignAuthInterceptor(tokenContextService: TokenContextService): FeignAuthInterceptor =
        FeignAuthInterceptor(tokenContextService)

    /**
     * RestTemplate bean for HTTP client operations
     */
    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}