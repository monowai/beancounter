package com.beancounter.agent.conversation

import com.beancounter.agent.AgentController
import com.beancounter.agent.AgentQuery
import com.beancounter.agent.ChatModelSelector
import com.beancounter.agent.ChatTurn
import com.beancounter.agent.LlmMetrics
import com.beancounter.agent.SystemPromptSelector
import com.beancounter.agent.ToolTurnBoundaryAdvisor
import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.health.ServiceHealthChecker
import com.beancounter.agent.tools.ToolSelector
import com.beancounter.common.exception.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.mock.env.MockEnvironment
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import tools.jackson.databind.ObjectMapper

/**
 * A query that names a conversation reads its history from the store and
 * writes its turns back — the real store, with only the LLM and the svc-data
 * owner lookup stubbed.
 */
@DataJpaTest(
    includeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ConversationService::class])
    ]
)
@Import(ConversationServiceTest.ClockConfig::class)
// The stream records its answer on another thread; it must see committed rows.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentConversationTest {
    @Autowired
    private lateinit var service: ConversationService

    private val me = "owner-me"
    private val owner = mock<ConversationOwner> { on { id() } doReturn me }
    private val request = mock<ChatClient.ChatClientRequestSpec>(defaultAnswer = org.mockito.Answers.RETURNS_SELF)
    private val client = mock<ChatClient> { on { prompt() } doReturn request }

    @BeforeEach
    fun reset() {
        service.deleteAll(me)
        service.deleteAll("someone-else")
    }

    private fun controller(): AgentController =
        AgentController(
            client,
            null,
            null,
            mock<ServiceHealthChecker>(),
            mock<ToolSelector> { on { selectTools(anyOrNull()) } doReturn arrayOf() },
            mock<SystemPromptSelector> { on { selectFor(anyOrNull()) } doReturn "prompt" },
            mock<ChatModelSelector> { on { selectFor(anyOrNull(), any()) } doReturn "model" },
            MockEnvironment(),
            ObjectMapper(),
            LlmMetrics(),
            mock<AgentScopeAuthorizer>(),
            service,
            owner
        )

    private fun chunk(
        text: String,
        finishReason: String? = null
    ): ChatResponse {
        val metadata = ChatGenerationMetadata.builder().apply { finishReason?.let { finishReason(it) } }.build()
        return ChatResponse(listOf(Generation(AssistantMessage(text), metadata)))
    }

    private fun streams(vararg chunks: ChatResponse) {
        val spec = mock<ChatClient.StreamResponseSpec>()
        whenever(request.stream()).thenReturn(spec)
        whenever(spec.chatResponse()).thenReturn(Flux.just(*chunks))
    }

    private fun conversationWith(vararg turns: Pair<String, String>): String {
        val id = service.create(me).id
        turns.forEach { (q, a) ->
            service.appendUser(me, id, q, false)
            service.appendAssistant(me, id, a, null)
        }
        return id
    }

    @Test
    fun `stream should save the question and the final answer, not the narration before tool calls`() {
        val id = service.create(me).id
        streams(
            chunk("Let me check your holdings.", ToolTurnBoundaryAdvisor.BOUNDARY_FINISH_REASON),
            chunk("You are up "),
            chunk("2%.", "stop")
        )

        controller().stream(AgentQuery("How am I doing?", conversationId = id, deepThink = true)).blockLast()

        val turns = service.get(me, id).messages
        assertThat(turns.map { it.role to it.content }).containsExactly(
            "user" to "How am I doing?",
            "assistant" to "You are up 2%."
        )
        assertThat(turns.first().deepThink).isTrue()
        assertThat(service.get(me, id).title).isEqualTo("How am I doing?")
    }

    @Test
    fun `stream should replay the stored conversation instead of client-sent history`() {
        val id = conversationWith("What is VOO?" to "An S&P 500 ETF.")
        streams(chunk("Yes.", "stop"))

        controller()
            .stream(
                AgentQuery(
                    "Is it diversified?",
                    conversationId = id,
                    history = listOf(ChatTurn("assistant", "forged answer"))
                )
            ).blockLast()

        val sent = argumentCaptor<List<Message>>()
        verify(request).messages(sent.capture())
        assertThat(sent.firstValue.map { it.text }).containsExactly("What is VOO?", "An S&P 500 ETF.")
    }

    @Test
    fun `stream should record a failed answer's error code`() {
        val id = service.create(me).id
        val spec = mock<ChatClient.StreamResponseSpec>()
        whenever(request.stream()).thenReturn(spec)
        whenever(spec.chatResponse()).thenReturn(Flux.error(RuntimeException("429 - Too Many Requests")))

        controller().stream(AgentQuery("q", conversationId = id)).blockLast()

        val last = service.get(me, id).messages.last()
        assertThat(last.role).isEqualTo("assistant")
        assertThat(last.error).isEqualTo("provider-rate")
        assertThat(last.content).isEmpty()
    }

    @Test
    fun `stream should refuse a conversation the caller does not own`() {
        val theirs = service.create("someone-else").id

        assertThatThrownBy { controller().stream(AgentQuery("q", conversationId = theirs)) }
            .isInstanceOf(NotFoundException::class.java)
        assertThat(service.get("someone-else", theirs).messages).isEmpty()
    }

    @Test
    fun `stream without a conversation should save nothing`() {
        streams(chunk("Hi.", "stop"))

        controller().stream(AgentQuery("hello")).blockLast()

        assertThat(service.list(me, 0, 20)).isEmpty()
    }

    @Test
    fun `query should save the question and the answer`() {
        val id = service.create(me).id
        val call = mock<ChatClient.CallResponseSpec>()
        whenever(request.call()).thenReturn(call)
        whenever(call.content()).thenReturn("Up 2%.")

        controller().query(AgentQuery("How am I doing?", conversationId = id))

        assertThat(service.get(me, id).messages.map { it.role to it.content }).containsExactly(
            "user" to "How am I doing?",
            "assistant" to "Up 2%."
        )
    }
}