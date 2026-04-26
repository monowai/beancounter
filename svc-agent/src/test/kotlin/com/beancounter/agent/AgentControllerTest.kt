package com.beancounter.agent

import com.beancounter.agent.health.AgentHealthResponse
import com.beancounter.agent.health.ServiceHealthChecker
import com.beancounter.agent.health.ServiceStatus
import com.beancounter.agent.tools.ToolSelector
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.mock.env.MockEnvironment
import reactor.core.publisher.Flux

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
            environment,
            ObjectMapper()
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

    @Test
    fun `stream emits a single error event when no LLM is configured`() {
        val events =
            controller(chatClient = null)
                .stream(AgentQuery("hello"))
                .collectList()
                .block()!!

        assertThat(events).hasSize(1)
        assertThat(events[0].event()).isEqualTo("error")
        assertThat(events[0].data()).contains("No LLM")
    }

    @Test
    fun `stream emits token chunks then a done event`() {
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val streamResponse = mock<ChatClient.StreamResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.stream()).thenReturn(streamResponse)
        whenever(streamResponse.content()).thenReturn(Flux.just("Hello", " world"))

        val events =
            controller(chatClient = client)
                .stream(AgentQuery("hi"))
                .collectList()
                .block()!!

        assertThat(events).hasSize(3)
        assertThat(events[0].event()).isEqualTo("token")
        assertThat(events[0].data()).isEqualTo("Hello")
        assertThat(events[1].event()).isEqualTo("token")
        assertThat(events[1].data()).isEqualTo(" world")

        // Parse the `done` payload as JSON instead of substring matching so
        // formatting / field-order changes can't quietly break the contract.
        assertThat(events[2].event()).isEqualTo("done")
        val done = ObjectMapper().readTree(events[2].data())
        assertThat(done["model"].asText()).isEqualTo("test-model-id")
        assertThat(done["chars"].asLong()).isEqualTo(11L)
        assertThat(done["elapsed_ms"].asLong()).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `stream emits opaque error when stream setup itself throws before any Flux`() {
        // Regression test for the Flux.defer hardening: a synchronous
        // exception thrown by ChatClient.prompt().stream() must surface as
        // the same SSE error envelope as a Flux.error mid-stream, not as a
        // raw 500 response without an SSE body.
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.stream()).thenThrow(IllegalStateException("boom"))

        val events =
            controller(chatClient = client)
                .stream(AgentQuery("hi"))
                .collectList()
                .block()!!

        assertThat(events).hasSize(1)
        assertThat(events[0].event()).isEqualTo("error")
        assertThat(events[0].data()).isEqualTo("agent-error")
        assertThat(events[0].data()).doesNotContain("boom")
    }

    @Test
    fun `stream emits an error event when the upstream Flux fails`() {
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val streamResponse = mock<ChatClient.StreamResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.stream()).thenReturn(streamResponse)
        whenever(streamResponse.content()).thenReturn(Flux.error(RuntimeException("boom")))

        val events =
            controller(chatClient = client)
                .stream(AgentQuery("hi"))
                .collectList()
                .block()!!

        // Error path may emit a partial token chunk before the error fires —
        // we only care that the LAST event is the opaque error envelope.
        // The raw exception message is logged server-side, never returned.
        val last = events.last()
        assertThat(last.event()).isEqualTo("error")
        assertThat(last.data()).isEqualTo("agent-error")
        assertThat(last.data()).doesNotContain("boom")
    }
}