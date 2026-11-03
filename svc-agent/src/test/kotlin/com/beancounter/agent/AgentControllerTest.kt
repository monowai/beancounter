package com.beancounter.agent

import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.conversation.InFlightTurns
import com.beancounter.agent.health.AgentHealthResponse
import com.beancounter.agent.health.ServiceHealthChecker
import com.beancounter.agent.health.ServiceStatus
import com.beancounter.agent.tools.ToolSelector
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
private const val EVENT_RESET = "reset"
private const val OPAQUE_ERROR = "agent-error"

/**
 * Build a minimal Spring AI [ChatResponse] carrying a single text chunk —
 * mirrors what `ChatClient.stream().chatResponse()` emits per LLM token
 * batch in production. Tests prefer this over a Mockito mock so type
 * changes in Spring AI surface as compile errors, not runtime NPEs.
 *
 * [finishReason] mirrors the finish-reason chunk a provider emits when a
 * generation turn ends — e.g. `"TOOL_CALLS"` for a narration turn about to
 * call tools, `"STOP"` for the final answer.
 */
private fun textResponse(
    chunk: String,
    finishReason: String? = null
): ChatResponse {
    val generation =
        if (finishReason == null) {
            Generation(AssistantMessage(chunk))
        } else {
            Generation(
                AssistantMessage(chunk),
                org.springframework.ai.chat.metadata.ChatGenerationMetadata
                    .builder()
                    .finishReason(finishReason)
                    .build()
            )
        }
    return ChatResponse(listOf(generation))
}

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
    private val environment = MockEnvironment().withProperty("agent.provider", "anthropic")

    // Default to a no-op authorizer so existing tests (that don't set up a
    // SecurityContext) aren't affected by the scope check. The authz contract
    // itself is verified in AgentControllerAuthzTest.
    private val permissiveAuthorizer =
        mock<AgentScopeAuthorizer> {
            on { authorize() } doAnswer { }
            on { verified() } doAnswer { true }
        }

    private fun controller(
        chatClient: ChatClient? = null,
        healthChecker: ServiceHealthChecker = stubChecker(),
        scopeAuthorizer: AgentScopeAuthorizer = permissiveAuthorizer,
        clock: java.time.Clock = java.time.Clock.systemUTC()
    ): AgentController =
        agentController(
            chatClient = chatClient,
            healthChecker = healthChecker,
            toolSelector = toolSelector,
            systemPromptSelector = systemPromptSelector,
            chatModelSelector = chatModelSelector,
            environment = environment,
            scopeAuthorizer = scopeAuthorizer,
            clock = clock
        )

    private fun prompts(clock: java.time.Clock = java.time.Clock.systemUTC()): TurnPrompts =
        TurnPrompts(
            toolSelector,
            systemPromptSelector,
            ModelRouting(null, null, null, chatModelSelector, environment),
            LlmMetrics(),
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
            prompts(fixed)
                .buildUserMessage(AgentQuery(question, context = mapOf("portfolioCode" to "VOO")))
        val withoutContext = prompts(fixed).buildUserMessage(AgentQuery(question))

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

    private fun answering(text: String): ChatClient {
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val callResponse = mock<ChatClient.CallResponseSpec>()
        whenever(request.call()).thenReturn(callResponse)
        whenever(callResponse.chatResponse()).thenReturn(null)
        whenever(callResponse.content()).thenReturn(text)
        return mock { on { prompt() } doReturn request }
    }

    private fun routing(
        thinking: ChatClient,
        fast: ChatClient,
        reasons: Boolean
    ): AgentController {
        val selector =
            mock<ChatModelSelector> {
                on { selectFor(anyOrNull(), any()) } doReturn "test-model-id"
                on { reasonsFor(anyOrNull()) } doReturn reasons
            }
        return agentController(
            chatClient = thinking,
            fastChatClient = fast,
            healthChecker = stubChecker(),
            toolSelector = toolSelector,
            systemPromptSelector = systemPromptSelector,
            chatModelSelector = selector,
            environment = MockEnvironment().withProperty("agent.provider", "deepseek"),
            scopeAuthorizer = permissiveAuthorizer
        )
    }

    @Test
    fun `a smart page's pre-canned prompt reasons on the thinking client`() {
        val thinking = answering("reasoned")
        val fast = answering("fast")

        val response =
            routing(thinking, fast, reasons = true)
                .query(AgentQuery("Review my watchlist", context = mapOf("page" to "Watchlist")))

        assertThat(response.body?.response).isEqualTo("reasoned")
    }

    @Test
    fun `any other page's pre-canned prompt stays on the fast client`() {
        val thinking = answering("reasoned")
        val fast = answering("fast")

        val response =
            routing(thinking, fast, reasons = false)
                .query(AgentQuery("Summarise", context = mapOf("page" to "Portfolio Review")))

        assertThat(response.body?.response).isEqualTo("fast")
    }

    @Test
    fun `buildOptions gives a reasoning turn the thinking budget`() {
        val ctrl =
            ModelRouting(
                null,
                null,
                null,
                chatModelSelector,
                MockEnvironment().withProperty("agent.provider", "deepseek")
            )

        val opts =
            (
                ctrl.buildOptions("deepseek-flash", deepThink = false, think = true)
                    as org.springframework.ai.deepseek.DeepSeekChatOptions.Builder
            ).build()

        assertThat(opts.maxTokens).isEqualTo(16384)
    }

    private fun modelHearing(questions: org.mockito.kotlin.KArgumentCaptor<String>): ChatClient {
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val callResponse = mock<ChatClient.CallResponseSpec>()
        whenever(request.user(questions.capture())).thenReturn(request)
        whenever(request.call()).thenReturn(callResponse)
        whenever(callResponse.chatResponse()).thenReturn(null)
        whenever(callResponse.content()).thenReturn("reviewed")
        return mock { on { prompt() } doReturn request }
    }

    @Test
    fun `a blank query on the asset review page reaches the model as the page's canned question`() {
        val questions = org.mockito.kotlin.argumentCaptor<String>()
        val context = mapOf<String, Any>("page" to "Asset Review", "tickers" to "ABC", "market" to "NYSE")

        val response = controller(chatClient = modelHearing(questions)).query(AgentQuery(context = context))

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(questions.lastValue).endsWith(
            "\n\nProduce an Asset Review for ABC listed on NYSE. Cover company and sector context, " +
                "current sentiment from recent news, corporate-action history (dividends and splits in the " +
                "last 12 months), and qualitative risk callouts. Stay at the ticker level — do not assume " +
                "the user holds it."
        )
        assertThat(response.body?.query).startsWith("Produce an Asset Review for ABC listed on NYSE.")
    }

    @Test
    fun `a caller's own question is asked in place of the page's canned question`() {
        val questions = org.mockito.kotlin.argumentCaptor<String>()
        val context = mapOf<String, Any>("page" to "Asset Review", "tickers" to "ABC")

        controller(chatClient = modelHearing(questions)).query(AgentQuery("Is it cheap?", context = context))

        assertThat(questions.lastValue).endsWith("\n\nIs it cheap?").doesNotContain("Produce an Asset Review")
    }

    @Test
    fun `a blank query on a page without a canned question is a bad request`() {
        val ctrl = controller(chatClient = answering("never"))
        val blank = AgentQuery(" ", context = mapOf("page" to "Settings"))

        assertThatThrownBy { ctrl.query(blank) }
            .isInstanceOf(com.beancounter.common.exception.BusinessException::class.java)
        assertThatThrownBy { ctrl.stream(blank) }
            .isInstanceOf(com.beancounter.common.exception.BusinessException::class.java)
    }

    @Test
    fun `query still answers when saving the answer to its conversation fails`() {
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val callResponse = mock<ChatClient.CallResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.call()).thenReturn(callResponse)
        whenever(callResponse.chatResponse()).thenReturn(null)
        whenever(callResponse.content()).thenReturn("Up 2%.")
        val conversations =
            mock<com.beancounter.agent.conversation.ConversationService> {
                on { history(any(), any(), any()) } doReturn emptyList()
                on { titleOf(any(), any()) } doReturn "Portfolio"
                on { stateOf(any(), any()) } doReturn
                    com.beancounter.agent.conversation.ConversationState(
                        com.beancounter.agent.conversation.ConversationKind.CHAT,
                        hasTurns = false
                    )
                on { appendAssistant(any(), any(), any(), anyOrNull()) } doAnswer {
                    throw IllegalStateException("agent database unavailable")
                }
            }
        val owner = mock<com.beancounter.agent.conversation.ConversationOwner> { on { id() } doReturn "me" }
        val ctrl =
            agentController(
                chatClient = client,
                healthChecker = stubChecker(),
                toolSelector = toolSelector,
                systemPromptSelector = systemPromptSelector,
                chatModelSelector = chatModelSelector,
                environment = environment,
                scopeAuthorizer = permissiveAuthorizer,
                conversations = conversations,
                conversationOwner = owner
            )

        val response = ctrl.query(AgentQuery("How am I doing?", conversationId = "c1"))

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body?.response).isEqualTo("Up 2%.")
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
    fun `query returns 402 and provider-quota when the provider is out of credit`() {
        // An exhausted credit balance is an administrative state on our LLM
        // account, not a fault in the request. It must not land in the same
        // 500 agent-error bucket as a genuine failure, or the UI can only ever
        // tell the user something broke.
        val client = mock<ChatClient>()
        whenever(client.prompt()).thenThrow(outOfCreditSync())

        val response = controller(chatClient = client).query(AgentQuery("anything"))

        assertThat(response.statusCode.value()).isEqualTo(402)
        assertThat(response.body?.error).isEqualTo("provider-quota")
        // The provider's raw text never reaches the browser; the UI renders
        // its own copy off the code.
        assertThat(response.body?.response).doesNotContain("Insufficient Balance")
    }

    @Test
    fun `query maps rate limits and timeouts to their own statuses`() {
        val rateLimited = mock<ChatClient>()
        whenever(rateLimited.prompt()).thenThrow(RuntimeException("429 - rate_limit_exceeded"))
        val rateResponse = controller(chatClient = rateLimited).query(AgentQuery("anything"))
        assertThat(rateResponse.statusCode.value()).isEqualTo(429)
        assertThat(rateResponse.body?.error).isEqualTo("provider-rate")

        val timedOut = mock<ChatClient>()
        whenever(timedOut.prompt()).thenThrow(RuntimeException("Read timed out"))
        val timeoutResponse = controller(chatClient = timedOut).query(AgentQuery("anything"))
        assertThat(timeoutResponse.statusCode.value()).isEqualTo(504)
        assertThat(timeoutResponse.body?.error).isEqualTo("provider-timeout")
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
        // A code, matching what /query puts in AgentResponse.error — the UI
        // renders the wording, so prose here would print raw to the user.
        assertThat(events[0].data()).isEqualTo("no-llm")
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
    fun `stream emits reset after a narration turn but not after the final stop turn`() {
        // Reproduces the reported bug: the model narrates ("I'll gather the
        // data needed...") before calling tools, then answers for real. The
        // narration turn's trailing chunk carries finishReason=TOOL_CALLS; the
        // final answer's carries STOP. Only the former should trigger a
        // client-side buffer reset.
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
                    textResponse("I'll gather the data needed for this briefing…", finishReason = "TOOL_CALLS"),
                    textResponse("Final answer.", finishReason = "STOP")
                )
            )

        val events =
            controller(chatClient = client)
                .stream(AgentQuery("hi"))
                .collectList()
                .block()!!

        assertThat(events.map { it.event() })
            .containsExactly(EVENT_TOKEN, EVENT_RESET, EVENT_TOKEN, EVENT_DONE)
        assertThat(events[0].data()).isEqualTo("I'll gather the data needed for this briefing…")
        assertThat(events[2].data()).isEqualTo("Final answer.")

        // Narration chars are still counted — totalChars feeds the
        // connect-retry guard and telemetry, and must not reset on `reset`.
        val done = ObjectMapper().readTree(events.last().data())
        val expectedChars = "I'll gather the data needed for this briefing…".length + "Final answer.".length
        assertThat(done["chars"].asLong()).isEqualTo(expectedChars.toLong())
    }

    @Test
    fun `stream reports truncation when the turn hits the token cap without answering`() {
        // Observed on kauri: an Independence question filled the context window
        // with tool results (prompt_tokens=116608), so the model spent its
        // remaining output budget on tool calls and reasoning and finished on
        // `length` with response_chars=0. The stream then closed cleanly with
        // no token event at all and bc-view rendered an empty assistant bubble
        // — a silent failure the reader can neither understand nor act on.
        val events = streamOf(textResponse("", finishReason = "length"))

        assertThat(events.map { it.event() }).containsExactly(EVENT_ERROR, EVENT_DONE)
        assertThat(events[0].data()).isEqualTo("answer-truncated")
    }

    @Test
    fun `stream reports an empty answer when the turn stops without producing text`() {
        val events = streamOf(textResponse("", finishReason = "STOP"))

        assertThat(events.map { it.event() }).containsExactly(EVENT_ERROR, EVENT_DONE)
        assertThat(events[0].data()).isEqualTo("empty-answer")
    }

    @Test
    fun `stream reports an empty answer when the model emits nothing at all`() {
        // Metadata-only chunks carry no Generation, so there is no finish
        // reason to classify on — still a blank bubble without this guard.
        val events = streamOf(ChatResponse(emptyList()))

        assertThat(events.map { it.event() }).containsExactly(EVENT_ERROR, EVENT_DONE)
        assertThat(events[0].data()).isEqualTo("empty-answer")
    }

    @Test
    fun `stream stays silent about emptiness when the model did answer`() {
        // The guard keys off the whole stream's char count, not the last
        // chunk's: a normal answer whose trailing chunk is metadata-only must
        // not be reported as empty.
        val events =
            streamOf(
                textResponse("Final answer."),
                textResponse("", finishReason = "STOP")
            )

        assertThat(events.map { it.event() }).containsExactly(EVENT_TOKEN, EVENT_DONE)
    }

    @Test
    fun `stream reports truncation off a tool-turn narration that never reaches an answer`() {
        // Narration counts as content for the retry guard but not as an answer:
        // the model spoke, called tools, and then hit the cap. The user still
        // has no answer, so this must not pass as a successful turn.
        val events =
            streamOf(
                textResponse("Let me gather that…", finishReason = "TOOL_CALLS"),
                textResponse("", finishReason = "length")
            )

        assertThat(events.map { it.event() })
            .containsExactly(EVENT_TOKEN, EVENT_RESET, EVENT_ERROR, EVENT_DONE)
        assertThat(events[2].data()).isEqualTo("answer-truncated")
    }

    @Test
    fun `query reports an empty answer rather than returning a blank response`() {
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val callResponse = mock<ChatClient.CallResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.call()).thenReturn(callResponse)
        whenever(callResponse.chatResponse()).thenReturn(textResponse("", finishReason = "length"))

        val response = controller(chatClient = client).query(AgentQuery("hi"))

        assertThat(response.body?.error).isEqualTo("answer-truncated")
        assertThat(response.body?.response).isNotBlank()
        // 422, not 500: the request was well-formed and the call succeeded —
        // the model simply produced nothing. A 500 reads as a server fault and
        // would page us for a condition only the question can fix.
        assertThat(response.statusCode.value()).isEqualTo(422)
    }

    /** Drive `stream()` over a canned chunk sequence — the mock dance in one place. */
    private fun streamOf(vararg chunks: ChatResponse): List<org.springframework.http.codec.ServerSentEvent<String>> {
        val request =
            mock<ChatClient.ChatClientRequestSpec>(
                defaultAnswer = org.mockito.Answers.RETURNS_SELF
            )
        val streamResponse = mock<ChatClient.StreamResponseSpec>()
        val client = mock<ChatClient> { on { prompt() } doReturn request }
        whenever(request.stream()).thenReturn(streamResponse)
        whenever(streamResponse.chatResponse()).thenReturn(Flux.just(*chunks))
        return controller(chatClient = client)
            .stream(AgentQuery("hi"))
            .collectList()
            .block()!!
    }

    @Test
    fun `done payload omits model when ollama provider is active`() {
        // On ollama / openai profiles the per-call Anthropic model override is
        // skipped and the configured ChatClient picks the model. Reporting
        // `chatModelSelector.selectFor(...)` would mislead the client, so the
        // field must be omitted from the done envelope on those profiles.
        environment.withProperty("agent.provider", "ollama")
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
    fun `buildOptions on deepseek provider returns DeepSeekChatOptions with model + maxTokens`() {
        val env = MockEnvironment().withProperty("agent.provider", "deepseek")
        val ctrl = ModelRouting(null, null, null, chatModelSelector, env)
        // Spring AI 2.0: buildOptions returns a ChatOptions.Builder; build it to assert.
        val opts = ctrl.buildOptions("deepseek-flash", deepThink = false)?.build()
        assertThat(opts).isInstanceOf(org.springframework.ai.deepseek.DeepSeekChatOptions::class.java)
        val dsOpts = opts as org.springframework.ai.deepseek.DeepSeekChatOptions
        assertThat(dsOpts.model).isEqualTo("deepseek-flash")
        assertThat(dsOpts.maxTokens).isEqualTo(4096)
    }

    @Test
    fun `buildOptions on deepseek provider with deepThink raises maxTokens to 16k`() {
        val env = MockEnvironment().withProperty("agent.provider", "deepseek")
        val ctrl = ModelRouting(null, null, null, chatModelSelector, env)
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
    fun `buildOptions on deepseek provider with think raises maxTokens so reasoning cannot starve the answer`() {
        // kauri 2026-09-25: a Chat FAB question on v4-flash (thinking on) spent
        // the whole 4096 output budget on reasoning_content and finished on
        // `length` with zero answer text — prompt was only 27k of a 1M window.
        val env = MockEnvironment().withProperty("agent.provider", "deepseek")
        val ctrl = ModelRouting(null, null, null, chatModelSelector, env)
        val opts =
            (
                ctrl.buildOptions("deepseek-flash", deepThink = false, think = true)
                    as org.springframework.ai.deepseek.DeepSeekChatOptions.Builder
            ).build()
        assertThat(opts.model).isEqualTo("deepseek-flash")
        assertThat(opts.maxTokens).isEqualTo(16384)
        // No effort here: the thinking client's body hook defaults it to `low`
        // (Spring AI's ReasoningEffort enum has no LOW) — beancounter#1128.
        assertThat(opts.reasoningEffort).isNull()
    }

    @Test
    fun `buildOptions on deepseek provider with think and deepThink keeps high reasoning effort`() {
        // The Chat FAB defaults to low effort; an explicit deep-think turn must not.
        val env = MockEnvironment().withProperty("agent.provider", "deepseek")
        val ctrl = ModelRouting(null, null, null, chatModelSelector, env)
        val opts =
            (
                ctrl.buildOptions("deepseek-v4-pro", deepThink = true, think = true)
                    as org.springframework.ai.deepseek.DeepSeekChatOptions.Builder
            ).build()
        assertThat(opts.reasoningEffort)
            .isEqualTo(org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest.ReasoningEffort.HIGH)
    }

    @Test
    fun `buildOptions on anthropic default with deepThink enables thinking`() {
        // agent.provider=anthropic (set on the shared environment) → real Anthropic surface.
        val ctrl = ModelRouting(null, null, null, chatModelSelector, environment)
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
        val ctrl = ModelRouting(null, null, null, chatModelSelector, environment)
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
    fun `buildOptions on ollama provider returns null for default model fallback`() {
        val env = MockEnvironment().withProperty("agent.provider", "ollama")
        val ctrl = ModelRouting(null, null, null, chatModelSelector, env)
        assertThat(ctrl.buildOptions("anything", deepThink = true)).isNull()
    }

    @Test
    fun `system prompt is the domain prompt alone on a first turn`() {
        assertThat(prompts().systemPromptFor(AgentQuery("hi"))).isEqualTo("test-system-prompt")
        assertThat(prompts().systemPromptFor(AgentQuery("hi", history = emptyList())))
            .isEqualTo("test-system-prompt")
    }

    @Test
    fun `system prompt carries the follow-up directive when history is present`() {
        // Asset Review and News prompts prescribe a full brief ("always call getNews first", fixed
        // sections). A follow-up on that brief must answer the question, not regenerate the brief.
        val followUp =
            AgentQuery(
                "how exposed is it to China?",
                history =
                    listOf(
                        ChatTurn("user", "Produce an Asset Review for AAPL"),
                        ChatTurn("assistant", "## AAPL — Bullish ...")
                    )
            )

        val prompt = prompts().systemPromptFor(followUp)

        assertThat(prompt).startsWith("test-system-prompt")
        assertThat(prompt).endsWith(DomainSystemPrompts.FOLLOW_UP)
    }

    @Test
    fun `historyMessages returns empty list when history is null`() {
        assertThat(prompts().historyMessages(AgentQuery("hi"))).isEmpty()
    }

    @Test
    fun `historyMessages returns empty list when history is empty`() {
        assertThat(prompts().historyMessages(AgentQuery("hi", history = emptyList()))).isEmpty()
    }

    @Test
    fun `historyMessages maps roles to Message types preserving order`() {
        val history =
            listOf(
                ChatTurn("user", "what's my NZD exposure?"),
                ChatTurn("assistant", "which portfolio — Kiwi or Global?"),
                ChatTurn("user", "Kiwi")
            )

        val messages = prompts().historyMessages(AgentQuery("hi", history = history))

        assertThat(messages).containsExactly(
            UserMessage("what's my NZD exposure?"),
            AssistantMessage("which portfolio — Kiwi or Global?"),
            UserMessage("Kiwi")
        )
    }

    @Test
    fun `historyMessages truncates to the trailing 6 turns`() {
        val history = (1..10).map { ChatTurn("user", "turn $it") }

        val messages = prompts().historyMessages(AgentQuery("hi", history = history))

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