package com.beancounter.agent

import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.health.AgentHealthResponse
import com.beancounter.agent.health.ServiceHealthChecker
import com.beancounter.agent.health.ServiceStatus
import com.beancounter.agent.tools.ToolSelector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.mock.env.MockEnvironment
import reactor.core.publisher.Flux
import tools.jackson.databind.ObjectMapper

private const val EVENT_TOKEN = "token"
private const val EVENT_DONE = "done"
private const val EVENT_ERROR = "error"
private const val OPAQUE_ERROR = "agent-error"

/**
 * Build a minimal Spring AI [ChatResponse] carrying a single text chunk —
 * mirrors what `ChatClient.stream().chatResponse()` emits per LLM token
 * batch in production. Tests prefer this over a Mockito mock so type
 * changes in Spring AI surface as compile errors, not runtime NPEs.
 */
private fun textResponse(chunk: String): ChatResponse = ChatResponse(listOf(Generation(AssistantMessage(chunk))))

/**
 * Reproduce the production transient-DNS failure shape: a Spring
 * [org.springframework.web.reactive.function.client.WebClientRequestException]
 * whose cause chain ends in [java.nio.channels.UnresolvedAddressException] —
 * exactly what bc-agent logged when api.deepseek.com briefly failed to resolve.
 */
private fun transientConnectError(): Throwable {
    val dns = java.nio.channels.UnresolvedAddressException()
    val connect = java.net.ConnectException("Connection failed").apply { initCause(dns) }
    return org.springframework.web.reactive.function.client.WebClientRequestException(
        connect,
        org.springframework.http.HttpMethod.POST,
        java.net.URI.create("https://api.deepseek.com/chat/completions"),
        org.springframework.http.HttpHeaders()
    )
}

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
            on { selectFor(anyOrNull(), any()) } doReturn "test-model-id"
        }

    // Empty active profiles → controller treats this as the Anthropic default
    // path. Tests that need ollama / openai branching can override.
    private val environment = MockEnvironment()

    // Default to a no-op authorizer so existing tests (that don't set up a
    // SecurityContext) aren't affected by the scope check. The authz contract
    // itself is verified in AgentControllerAuthzTest.
    private val permissiveAuthorizer =
        mock<AgentScopeAuthorizer> {
            on { authorize(anyOrNull()) } doAnswer { }
        }

    private fun controller(
        chatClient: ChatClient? = null,
        healthChecker: ServiceHealthChecker = stubChecker(),
        scopeAuthorizer: AgentScopeAuthorizer = permissiveAuthorizer,
        clock: java.time.Clock = java.time.Clock.systemUTC()
    ): AgentController =
        AgentController(
            chatClient,
            null, // fastChatClient (non-thinking) — routing not exercised by these unit tests
            // anthropicCacheOptions is optional; null is fine — the controller
            // gracefully omits .cacheOptions when the bean isn't present.
            null,
            healthChecker,
            toolSelector,
            systemPromptSelector,
            chatModelSelector,
            environment,
            ObjectMapper(),
            LlmMetrics(),
            scopeAuthorizer,
            clock
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
    fun `user message carries the current date so the model anchors to today not its training cutoff`() {
        // DeepSeek answered with a 2025 frame because nothing in the prompt bound "today" to a real
        // date. The user message must state the current date for every query.
        val fixed = java.time.Clock.fixed(java.time.Instant.parse("2026-06-06T10:00:00Z"), java.time.ZoneOffset.UTC)
        val question = "how is the market today?"

        val withContext =
            controller(clock = fixed)
                .buildUserMessage(AgentQuery(question, context = mapOf("portfolioCode" to "VOO")))
        val withoutContext = controller(clock = fixed).buildUserMessage(AgentQuery(question))

        assertThat(withContext).contains("2026-06-06")
        assertThat(withContext).contains("portfolioCode: VOO")
        assertThat(withContext).contains(question)
        // Even with no page context, the date must still be present.
        assertThat(withoutContext).contains("2026-06-06")
        assertThat(withoutContext).contains(question)
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
        assertThat(response.body?.error).isEqualTo(OPAQUE_ERROR)
    }

    @Test
    fun `stream emits a single error event when no LLM is configured`() {
        val events =
            controller(chatClient = null)
                .stream(AgentQuery("hello"))
                .collectList()
                .block()!!

        assertThat(events).hasSize(1)
        assertThat(events[0].event()).isEqualTo(EVENT_ERROR)
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
        whenever(streamResponse.chatResponse())
            .thenReturn(Flux.just(textResponse("Hello"), textResponse(" world")))

        val events =
            controller(chatClient = client)
                .stream(AgentQuery("hi"))
                .collectList()
                .block()!!

        assertThat(events).hasSize(3)
        assertThat(events[0].event()).isEqualTo(EVENT_TOKEN)
        assertThat(events[0].data()).isEqualTo("Hello")
        assertThat(events[1].event()).isEqualTo(EVENT_TOKEN)
        assertThat(events[1].data()).isEqualTo(" world")

        // Parse the `done` payload as JSON instead of substring matching so
        // formatting / field-order changes can't quietly break the contract.
        assertThat(events[2].event()).isEqualTo(EVENT_DONE)
        val done = ObjectMapper().readTree(events[2].data())
        assertThat(done["model"].asString()).isEqualTo("test-model-id")
        assertThat(done["chars"].asLong()).isEqualTo(11L)
        assertThat(done["elapsed_ms"].asLong()).isGreaterThanOrEqualTo(0L)
    }

    @Test
    fun `stream skips ChatResponse chunks that carry no Generation`() {
        // Spring AI emits trailing metadata-only ChatResponse chunks (token
        // usage, finishReason) where getResult() is null. Previously the
        // mapper accessed resp.result.output.text directly and NPE'd. The
        // chunk should be filtered out, leaving the surrounding text chunks
        // and the done envelope intact.
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val streamResponse = mock<ChatClient.StreamResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.stream()).thenReturn(streamResponse)
        whenever(streamResponse.chatResponse())
            .thenReturn(
                Flux.just(
                    textResponse("Hello"),
                    ChatResponse(emptyList()),
                    textResponse(" world")
                )
            )

        val events =
            controller(chatClient = client)
                .stream(AgentQuery("hi"))
                .collectList()
                .block()!!

        assertThat(events).hasSize(3)
        assertThat(events[0].data()).isEqualTo("Hello")
        assertThat(events[1].data()).isEqualTo(" world")
        assertThat(events[2].event()).isEqualTo(EVENT_DONE)
    }

    @Test
    fun `done payload omits model when ollama profile is active`() {
        // On ollama / openai profiles the per-call Anthropic model override is
        // skipped and the configured ChatClient picks the model. Reporting
        // `chatModelSelector.selectFor(...)` would mislead the client, so the
        // field must be omitted from the done envelope on those profiles.
        environment.setActiveProfiles("ollama")
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val streamResponse = mock<ChatClient.StreamResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.stream()).thenReturn(streamResponse)
        whenever(streamResponse.chatResponse()).thenReturn(Flux.just(textResponse("ok")))

        val events =
            controller(chatClient = client)
                .stream(AgentQuery("hi"))
                .collectList()
                .block()!!

        val done = ObjectMapper().readTree(events.last().data())
        assertThat(done.has("model")).isFalse()
        assertThat(done["chars"].asLong()).isEqualTo(2L)
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
        assertThat(events[0].event()).isEqualTo(EVENT_ERROR)
        assertThat(events[0].data()).isEqualTo(OPAQUE_ERROR)
        assertThat(events[0].data()).doesNotContain("boom")
    }

    @Test
    fun `classifyError returns provider-quota for Anthropic credit balance errors`() {
        val anthropicQuota =
            RuntimeException(
                "Response exception, Status: [400 BAD_REQUEST], " +
                    "Body:[{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\"," +
                    "\"message\":\"Your credit balance is too low to access the Anthropic API.\"}}]"
            )
        assertThat(controller().classifyError(anthropicQuota))
            .isEqualTo("provider-quota")
    }

    @Test
    fun `classifyError returns provider-rate for HTTP 429 and rate-limit text`() {
        assertThat(controller().classifyError(RuntimeException("HTTP 429 Too Many Requests")))
            .isEqualTo("provider-rate")
        assertThat(controller().classifyError(RuntimeException("anthropic.rate_limit_exceeded")))
            .isEqualTo("provider-rate")
    }

    @Test
    fun `classifyError returns provider-timeout for TimeoutException and timeout text`() {
        assertThat(controller().classifyError(java.util.concurrent.TimeoutException()))
            .isEqualTo("provider-timeout")
        assertThat(controller().classifyError(RuntimeException("Read timed out")))
            .isEqualTo("provider-timeout")
    }

    @Test
    fun `classifyError falls back to agent-error for unknown failures`() {
        assertThat(controller().classifyError(RuntimeException("something exploded")))
            .isEqualTo(OPAQUE_ERROR)
        assertThat(controller().classifyError(RuntimeException(null as String?)))
            .isEqualTo(OPAQUE_ERROR)
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
        whenever(streamResponse.chatResponse())
            .thenReturn(Flux.error(RuntimeException("boom")))

        val events =
            controller(chatClient = client)
                .stream(AgentQuery("hi"))
                .collectList()
                .block()!!

        // Error path may emit a partial token chunk before the error fires —
        // we only care that the LAST event is the opaque error envelope.
        // The raw exception message is logged server-side, never returned.
        val last = events.last()
        assertThat(last.event()).isEqualTo(EVENT_ERROR)
        assertThat(last.data()).isEqualTo(OPAQUE_ERROR)
        assertThat(last.data()).doesNotContain("boom")
    }

    @Test
    fun `stream retries a transient connect failure then emits the recovered tokens`() {
        // The DeepSeek WebClient occasionally fails to resolve api.deepseek.com
        // (UnresolvedAddressException) for a sub-second DNS blip. Such a failure
        // happens at connection time — before any token is emitted — so the
        // stream must retry rather than surface a user-facing error.
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val streamResponse = mock<ChatClient.StreamResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.stream()).thenReturn(streamResponse)
        val attempts =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        whenever(streamResponse.chatResponse())
            .thenReturn(
                Flux.defer {
                    if (attempts.getAndIncrement() == 0) {
                        Flux.error(transientConnectError())
                    } else {
                        Flux.just(textResponse("recovered"))
                    }
                }
            )

        val events =
            controller(chatClient = client)
                .stream(AgentQuery("hi"))
                .collectList()
                .block()!!

        assertThat(attempts.get()).isEqualTo(2)
        assertThat(events.map { it.event() }).containsExactly(EVENT_TOKEN, EVENT_DONE)
        assertThat(events[0].data()).isEqualTo("recovered")
    }

    @Test
    fun `stream does not retry a non-transient error`() {
        // A provider-side 4xx is deterministic — retrying only delays the
        // inevitable error envelope and wastes provider quota.
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val streamResponse = mock<ChatClient.StreamResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.stream()).thenReturn(streamResponse)
        val attempts =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        whenever(streamResponse.chatResponse())
            .thenReturn(
                Flux.defer {
                    attempts.getAndIncrement()
                    Flux.error<ChatResponse>(RuntimeException("HTTP 400 invalid_request"))
                }
            )

        val events =
            controller(chatClient = client)
                .stream(AgentQuery("hi"))
                .collectList()
                .block()!!

        assertThat(attempts.get()).isEqualTo(1)
        assertThat(events).hasSize(1)
        assertThat(events[0].event()).isEqualTo(EVENT_ERROR)
    }

    @Test
    fun `stream does not retry a transient failure once tokens have been emitted`() {
        // Retrying after content has already streamed to the client would
        // duplicate the emitted tokens. Once the first token is out, a later
        // connectivity drop must surface as an error, not a silent re-run.
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val streamResponse = mock<ChatClient.StreamResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.stream()).thenReturn(streamResponse)
        val attempts =
            java.util.concurrent.atomic
                .AtomicInteger(0)
        whenever(streamResponse.chatResponse())
            .thenReturn(
                Flux.defer {
                    attempts.getAndIncrement()
                    Flux
                        .just(textResponse("partial"))
                        .concatWith(Flux.error(transientConnectError()))
                }
            )

        val events =
            controller(chatClient = client)
                .stream(AgentQuery("hi"))
                .collectList()
                .block()!!

        assertThat(attempts.get()).isEqualTo(1)
        assertThat(events.map { it.event() }).containsExactly(EVENT_TOKEN, EVENT_ERROR)
        assertThat(events[0].data()).isEqualTo("partial")
    }

    @Test
    fun `buildOptions on deepseek profile returns DeepSeekChatOptions with model + maxTokens`() {
        val env = MockEnvironment().apply { setActiveProfiles("deepseek") }
        val ctrl =
            AgentController(
                null,
                null, // fastChatClient
                null,
                stubChecker(),
                toolSelector,
                systemPromptSelector,
                chatModelSelector,
                env,
                ObjectMapper(),
                LlmMetrics(),
                permissiveAuthorizer
            )
        // Spring AI 2.0: buildOptions returns a ChatOptions.Builder; build it to assert.
        val opts = ctrl.buildOptions("deepseek-v4-flash", deepThink = false)?.build()
        assertThat(opts).isInstanceOf(org.springframework.ai.deepseek.DeepSeekChatOptions::class.java)
        val dsOpts = opts as org.springframework.ai.deepseek.DeepSeekChatOptions
        assertThat(dsOpts.model).isEqualTo("deepseek-v4-flash")
        assertThat(dsOpts.maxTokens).isEqualTo(4096)
    }

    @Test
    fun `buildOptions on deepseek profile with deepThink raises maxTokens to 16k`() {
        val env = MockEnvironment().apply { setActiveProfiles("deepseek") }
        val ctrl =
            AgentController(
                null,
                null, // fastChatClient
                null,
                stubChecker(),
                toolSelector,
                systemPromptSelector,
                chatModelSelector,
                env,
                ObjectMapper(),
                LlmMetrics(),
                permissiveAuthorizer
            )
        // Spring AI 2.0: buildOptions returns a ChatOptions.Builder; build() it.
        val opts =
            (
                ctrl.buildOptions("deepseek-v4-pro", deepThink = true)
                    as org.springframework.ai.deepseek.DeepSeekChatOptions.Builder
            ).build()
        assertThat(opts.model).isEqualTo("deepseek-v4-pro")
        assertThat(opts.maxTokens).isEqualTo(16384)
    }

    @Test
    fun `buildOptions on anthropic default with deepThink enables thinking`() {
        // Empty active profiles → anthropicActive = true (real Anthropic surface).
        val ctrl = controller()
        val opts =
            (
                ctrl.buildOptions("claude-opus-4-7", deepThink = true)
                    as org.springframework.ai.anthropic.AnthropicChatOptions.Builder
            ).build()
        assertThat(opts.model).isEqualTo("claude-opus-4-7")
        assertThat(opts.maxTokens).isEqualTo(16384)
        assertThat(opts.thinking).isNotNull
        // Spring AI 2.0 thinking is the anthropic-java ThinkingConfigParam.
        assertThat(opts.thinking!!.isEnabled()).isTrue()
        assertThat(opts.thinking!!.asEnabled().budgetTokens()).isEqualTo(4096L)
    }

    @Test
    fun `buildOptions on anthropic default without deepThink omits thinking`() {
        val ctrl = controller()
        val opts =
            (
                ctrl.buildOptions("claude-haiku-4-5-20251001", deepThink = false)
                    as org.springframework.ai.anthropic.AnthropicChatOptions.Builder
            ).build()
        assertThat(opts.model).isEqualTo("claude-haiku-4-5-20251001")
        // No deepThink → thinking is never enabled (null on the built options).
        assertThat(opts.thinking).isNull()
    }

    @Test
    fun `buildOptions on ollama profile returns null for default model fallback`() {
        val env = MockEnvironment().apply { setActiveProfiles("ollama") }
        val ctrl =
            AgentController(
                null,
                null, // fastChatClient
                null,
                stubChecker(),
                toolSelector,
                systemPromptSelector,
                chatModelSelector,
                env,
                ObjectMapper(),
                LlmMetrics(),
                permissiveAuthorizer
            )
        assertThat(ctrl.buildOptions("anything", deepThink = true)).isNull()
    }

    @Test
    fun `historyMessages returns empty list when history is null`() {
        assertThat(controller().historyMessages(AgentQuery("hi"))).isEmpty()
    }

    @Test
    fun `historyMessages returns empty list when history is empty`() {
        assertThat(controller().historyMessages(AgentQuery("hi", history = emptyList()))).isEmpty()
    }

    @Test
    fun `historyMessages maps roles to Message types preserving order`() {
        val history =
            listOf(
                ChatTurn("user", "what's my NZD exposure?"),
                ChatTurn("assistant", "which portfolio — Kiwi or Global?"),
                ChatTurn("user", "Kiwi")
            )

        val messages = controller().historyMessages(AgentQuery("hi", history = history))

        assertThat(messages).containsExactly(
            UserMessage("what's my NZD exposure?"),
            AssistantMessage("which portfolio — Kiwi or Global?"),
            UserMessage("Kiwi")
        )
    }

    @Test
    fun `historyMessages truncates to the trailing 6 turns`() {
        val history = (1..10).map { ChatTurn("user", "turn $it") }

        val messages = controller().historyMessages(AgentQuery("hi", history = history))

        assertThat(messages).hasSize(6)
        assertThat(messages).containsExactly(
            UserMessage("turn 5"),
            UserMessage("turn 6"),
            UserMessage("turn 7"),
            UserMessage("turn 8"),
            UserMessage("turn 9"),
            UserMessage("turn 10")
        )
    }

    @Test
    fun `query threads history onto the prompt before the current user message`() {
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val callResponse = mock<ChatClient.CallResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.call()).thenReturn(callResponse)
        whenever(callResponse.chatResponse()).thenReturn(null)
        whenever(callResponse.content()).thenReturn("Kiwi it is")
        val history = listOf(ChatTurn("assistant", "which portfolio?"))

        controller(chatClient = client).query(AgentQuery("Kiwi", history = history))

        org.mockito.kotlin
            .verify(request)
            .messages(listOf(AssistantMessage("which portfolio?")))
    }

    @Test
    fun `stream threads history onto the prompt before the current user message`() {
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val streamResponse = mock<ChatClient.StreamResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.stream()).thenReturn(streamResponse)
        whenever(streamResponse.chatResponse()).thenReturn(Flux.just(textResponse("ok")))
        val history = listOf(ChatTurn("user", "earlier question"))

        controller(chatClient = client)
            .stream(AgentQuery("follow-up", history = history))
            .collectList()
            .block()

        org.mockito.kotlin
            .verify(request)
            .messages(listOf(UserMessage("earlier question")))
    }
}