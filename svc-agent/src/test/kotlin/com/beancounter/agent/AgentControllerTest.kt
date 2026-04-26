package com.beancounter.agent

import com.beancounter.agent.health.AgentHealthResponse
import com.beancounter.agent.health.ServiceHealthChecker
import com.beancounter.agent.health.ServiceStatus
import com.beancounter.agent.tools.ToolSelector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.mock.env.MockEnvironment

/**
 * Unit tests for [AgentController]. The agent's actual behaviour is provided by
 * Spring AI tool calling, which is exercised end-to-end against a real LLM —
 * not in a unit test. Here we only check the controller's contract: health
 * delegates to the [ServiceHealthChecker], /query returns 503 when no
 * `ChatClient` is configured, 200 with the LLM's content when one is, and
 * 500 if the client throws.
 */
class AgentControllerTest {
    private val greenResponse =
        AgentHealthResponse(
            overallStatus = "GREEN",
            summary = "All services operational",
            services = listOf(ServiceStatus(name = "llm", status = "UP"))
        )

    private val toolSelector =
        mock<ToolSelector> {
            on { selectTools(anyOrNull()) } doReturn arrayOf()
        }

    private val systemPromptSelector =
        mock<SystemPromptSelector> {
            on { selectFor(anyOrNull()) } doReturn "test-system-prompt"
        }

    private val chatModelSelector =
        mock<ChatModelSelector> {
            on { selectFor(anyOrNull()) } doReturn "test-model-id"
        }

    // Empty active profiles → controller treats this as the Anthropic default
    // path. Tests that need ollama / openai branching can override.
    private val environment = MockEnvironment()

    private fun controller(
        chatClient: ChatClient? = null,
        healthChecker: ServiceHealthChecker = stubChecker()
    ): AgentController =
        AgentController(
            chatClient,
            // anthropicCacheOptions is optional; null is fine — the controller
            // gracefully omits .cacheOptions when the bean isn't present.
            null,
            healthChecker,
            toolSelector,
            systemPromptSelector,
            chatModelSelector,
            environment
        )

    private fun stubChecker(): ServiceHealthChecker =
        mock<ServiceHealthChecker> {
            on { check(any()) } doReturn greenResponse
        }

    @Test
    fun `health delegates to the service health checker`() {
        val checker = stubChecker()
        val health = controller(healthChecker = checker).health()

        assertThat(health.overallStatus).isEqualTo("GREEN")
        assertThat(health.summary).isEqualTo("All services operational")
    }

    @Test
    fun `health tells the checker the llm is absent when chatClient is null`() {
        val checker = stubChecker()
        controller(chatClient = null, healthChecker = checker).health()

        org.mockito.kotlin
            .verify(checker)
            .check(llmAvailable = false)
    }

    @Test
    fun `health tells the checker the llm is present when chatClient is wired`() {
        val checker = stubChecker()
        controller(chatClient = mock(), healthChecker = checker).health()

        org.mockito.kotlin
            .verify(checker)
            .check(llmAvailable = true)
    }

    @Test
    fun `query returns 503 when no LLM is configured`() {
        val response = controller(chatClient = null).query(AgentQuery("hello"))

        assertThat(response.statusCode.value()).isEqualTo(503)
        assertThat(response.body?.error).isEqualTo("no-llm")
    }

    @Test
    fun `query returns the LLM content for a configured chatClient`() {
        // RETURNS_SELF handles the fluent builder chain (user(), tools())
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val callResponse = mock<ChatClient.CallResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        // call() breaks out of the RETURNS_SELF chain
        whenever(request.call()).thenReturn(callResponse)
        whenever(callResponse.chatResponse()).thenReturn(null)
        whenever(callResponse.content()).thenReturn("hi from the model")

        val response = controller(chatClient = client).query(AgentQuery("hello"))

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body?.response).isEqualTo("hi from the model")
        assertThat(response.body?.error).isNull()
    }

    @Test
    fun `query returns 500 when the chatClient throws`() {
        val client = mock<ChatClient>()
        whenever(client.prompt()).thenThrow(IllegalStateException("model exploded"))

        val response = controller(chatClient = client).query(AgentQuery("anything"))

        assertThat(response.statusCode.value()).isEqualTo(500)
        assertThat(response.body?.error).isEqualTo("agent-error")
    }
}