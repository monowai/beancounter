package com.beancounter.agent.quota

import com.beancounter.agent.AgentController
import com.beancounter.agent.AgentQuery
import com.beancounter.agent.ChatModelSelector
import com.beancounter.agent.LlmMetrics
import com.beancounter.agent.SystemPromptSelector
import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.conversation.ConversationOwner
import com.beancounter.agent.conversation.ConversationService
import com.beancounter.agent.conversation.ConversationServiceTest
import com.beancounter.agent.conversation.ConversationTitler
import com.beancounter.agent.conversation.InFlightTurns
import com.beancounter.agent.conversation.SteppingClock
import com.beancounter.agent.health.ServiceHealthChecker
import com.beancounter.agent.review.AssetReviewCacheService
import com.beancounter.agent.review.AssetReviewRepository
import com.beancounter.agent.tools.ToolSelector
import com.beancounter.auth.model.AuthConstants
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * The weekly quota on AI turns for callers without `beancounter:ai`, against
 * the real store: answered turns are counted over a rolling window, cache hits
 * and failures are not, and an exhausted caller is refused before any model
 * call or conversation write. Only the LLM and the svc-data owner lookup are
 * stubbed.
 */
@DataJpaTest(
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [
                ConversationService::class,
                InFlightTurns::class,
                AssetReviewCacheService::class,
                AgentQuota::class
            ]
        )
    ],
    properties = [
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:agent-quota;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "beancounter.agent.quota.weekly=3",
        "beancounter.agent.quota.contact=help@example.com"
    ]
)
@Import(ConversationServiceTest.ClockConfig::class)
// The stream records on another thread; it must see committed rows.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentQuotaTest {
    @Autowired
    private lateinit var quota: AgentQuota

    @Autowired
    private lateinit var conversations: ConversationService

    @Autowired
    private lateinit var reviews: AssetReviewCacheService

    @Autowired
    private lateinit var reviewRows: AssetReviewRepository

    @Autowired
    private lateinit var clock: SteppingClock

    private val me = "quota-me"
    private val owner = mock<ConversationOwner> { on { id() } doAnswer { me } }
    private val request = mock<ChatClient.ChatClientRequestSpec>(defaultAnswer = org.mockito.Answers.RETURNS_SELF)
    private val client = mock<ChatClient> { on { prompt() } doAnswer { request } }
    private var modelCalls = 0
    private var modelFails = false

    @BeforeEach
    fun reset() {
        quota.forget(me)
        quota.clearLimit(me)
        reviewRows.deleteAll()
        conversations.deleteAll(me)
        modelCalls = 0
        modelFails = false
        actAs(AuthConstants.SCOPE_PREVIEW, AuthConstants.SCOPE_VERIFIED)
        whenever(request.call()).thenAnswer {
            modelCalls++
            if (modelFails) throw IllegalStateException("model down")
            mock<ChatClient.CallResponseSpec> { on { content() } doAnswer { "An answer." } }
        }
        whenever(request.stream()).thenAnswer {
            modelCalls++
            val metadata = ChatGenerationMetadata.builder().finishReason("stop").build()
            val spec = mock<ChatClient.StreamResponseSpec>()
            whenever(spec.chatResponse())
                .thenReturn(Flux.just(ChatResponse(listOf(Generation(AssistantMessage("Streamed."), metadata)))))
            spec
        }
    }

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun actAs(vararg authorities: String) {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("user", "n/a", authorities.map(::SimpleGrantedAuthority))
    }

    private fun controller(): AgentController =
        AgentController(
            client,
            null,
            null,
            mock<ServiceHealthChecker>(),
            mock<ToolSelector> { on { selectTools(anyOrNull()) } doAnswer { arrayOf() } },
            mock<SystemPromptSelector> { on { selectFor(anyOrNull()) } doAnswer { "prompt" } },
            mock<ChatModelSelector> { on { selectFor(anyOrNull(), any()) } doAnswer { "model" } },
            MockEnvironment(),
            ObjectMapper(),
            LlmMetrics(),
            AgentScopeAuthorizer(),
            conversations,
            owner,
            mock<ConversationTitler>(),
            InFlightTurns(),
            clock,
            reviewCache = reviews,
            quota = quota
        )

    private val chat = AgentQuery("How am I doing?", context = mapOf("page" to "Portfolio AI Overview"))
    private val news =
        AgentQuery("Get news for NATO", context = mapOf("page" to "News & Sentiment", "tickers" to "NATO"))
    private val assetReview =
        AgentQuery(
            context =
                mapOf(
                    "page" to "Asset Review",
                    "tickers" to "NATO",
                    "market" to "NASDAQ"
                )
        )

    private fun ask(
        query: AgentQuery,
        times: Int = 1
    ) = (1..times).map { controller().query(query) }.last()

    @Test
    fun `a preview user should be refused with ai-quota once the weekly turns are answered`() {
        ask(chat, times = 3)

        val refused = controller().query(chat)

        assertThat(refused.statusCode.value()).isEqualTo(429)
        assertThat(refused.body?.error).isEqualTo("ai-quota")
        assertThat(modelCalls).isEqualTo(3)
    }

    @Test
    fun `a refused turn in a conversation should record no question`() {
        ask(chat, times = 3)
        val id = conversations.create(me).id

        controller().query(chat.copy(conversationId = id))

        assertThat(conversations.get(me, id).messages).isEmpty()
    }

    @Test
    fun `an exhausted stream should end with the ai-quota code and call no model`() {
        ask(chat, times = 3)

        val events =
            controller()
                .stream(chat)
                .collectList()
                .block(Duration.ofSeconds(5))
                .orEmpty()

        assertThat(events.map { it.event() to it.data() }).containsExactly("error" to "ai-quota")
        assertThat(modelCalls).isEqualTo(3)
    }

    @Test
    fun `an answered stream should count as a turn`() {
        controller().stream(chat).collectList().block(Duration.ofSeconds(5))

        assertThat(quota.statusOf(me).used).isEqualTo(1)
    }

    @Test
    fun `a failed turn should not count`() {
        modelFails = true
        ask(chat, times = 2)
        modelFails = false

        assertThat(quota.statusOf(me).used).isZero()
        assertThat(ask(chat).statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `news should never count or be refused`() {
        ask(chat, times = 3)

        val answered = ask(news, times = 2)

        assertThat(answered.statusCode.value()).isEqualTo(200)
        assertThat(quota.statusOf(me).used).isEqualTo(3)
    }

    @Test
    fun `a news page question inside a conversation should count like any chat`() {
        val id = conversations.create(me).id

        ask(news.copy(conversationId = id))

        assertThat(quota.statusOf(me).used).isEqualTo(1)
    }

    @Test
    fun `a live asset review should count but a cache hit should not, even at the limit`() {
        ask(assetReview)
        assertThat(quota.statusOf(me).used).isEqualTo(1)
        ask(chat, times = 2)

        val cached = ask(assetReview)

        assertThat(cached.statusCode.value()).isEqualTo(200)
        assertThat(cached.body?.cached).isTrue()
        assertThat(quota.statusOf(me).used).isEqualTo(3)
    }

    @Test
    fun `a streamed asset review cache hit should not count`() {
        controller().stream(assetReview).collectList().block(Duration.ofSeconds(5))
        controller().stream(assetReview).collectList().block(Duration.ofSeconds(5))

        assertThat(modelCalls).isEqualTo(1)
        assertThat(quota.statusOf(me).used).isEqualTo(1)
    }

    @Test
    fun `the ai role should be unlimited`() {
        actAs(AuthConstants.SCOPE_AI, AuthConstants.SCOPE_PREVIEW)

        val answered = ask(chat, times = 5)

        assertThat(answered.statusCode.value()).isEqualTo(200)
        assertThat(quota.statusOf(me).used).isZero()
    }

    @Test
    fun `a turn should stop counting seven days after it was answered`() {
        ask(chat)
        clock.advance(Duration.ofDays(1))
        ask(chat, times = 2)
        val status = quota.statusOf(me)
        assertThat(status.remaining).isZero()
        assertThat(status.resetAt).isEqualTo(clock.instant().minus(Duration.ofDays(1)).plus(Duration.ofDays(7)))

        clock.advance(Duration.ofDays(6))

        assertThat(quota.statusOf(me).remaining).isEqualTo(1)
        assertThat(ask(chat).statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `an administrator's limit for the user should replace the default`() {
        quota.setLimit(me, 1)
        ask(chat)

        assertThat(controller().query(chat).body?.error).isEqualTo("ai-quota")
        assertThat(quota.statusOf(me).limit).isEqualTo(1)

        quota.clearLimit(me)
        assertThat(quota.statusOf(me).limit).isEqualTo(3)
    }

    @Test
    fun `an unconfirmed email should get a cached asset review and nothing live`() {
        ask(assetReview)
        actAs(AuthConstants.SCOPE_PREVIEW)
        modelCalls = 0

        val cached = ask(assetReview)
        val refused = listOf(chat, news, assetReview.copy(refresh = true)).map { controller().query(it) }

        assertThat(cached.statusCode.value()).isEqualTo(200)
        assertThat(cached.body?.cached).isTrue()
        assertThat(refused.map { it.statusCode.value() to it.body?.error })
            .containsOnly(403 to "email-unverified")
        assertThat(modelCalls).isZero()
        assertThat(quota.statusOf(me).used).isEqualTo(1)
    }

    @Test
    fun `an unconfirmed email should be refused a live stream before any model call`() {
        actAs(AuthConstants.SCOPE_PREVIEW)

        val events =
            controller()
                .stream(assetReview)
                .collectList()
                .block(Duration.ofSeconds(5))
                .orEmpty()

        assertThat(events.map { it.event() to it.data() }).containsExactly("error" to "email-unverified")
        assertThat(modelCalls).isZero()
    }

    @Test
    fun `the ai role should not need a confirmed email`() {
        actAs(AuthConstants.SCOPE_AI)

        assertThat(ask(chat).statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `status should name the contact for raising the quota`() {
        assertThat(quota.statusOf(me).contact).isEqualTo("help@example.com")
        assertThat(quota.statusOf(me).resetAt).isNull()
    }
}