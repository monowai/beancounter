package com.beancounter.agent.conversation

import com.beancounter.agent.ChatTurn
import com.beancounter.common.exception.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test can move forward, so idle-age behaviour is observable. */
class SteppingClock(
    private var now: Instant
) : Clock() {
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

/**
 * Conversation store against a real database. Flyway runs the shipped
 * migrations and Hibernate validates the entities against them, so a
 * migration that drifts from the entity mapping fails here rather than in a
 * CrashLoopBackOff on kauri.
 */
@DataJpaTest(
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [ConversationService::class, InFlightTurns::class]
        )
    ],
    properties = [
        // Keep the configured datasource: the default embedded replacement
        // upper-cases unquoted identifiers, which the quoted naming strategy
        // then fails to find.
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:agent-conversations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
    ]
)
@Import(ConversationServiceTest.ClockConfig::class)
class ConversationServiceTest {
    @TestConfiguration
    class ClockConfig {
        @Bean
        fun clock(): SteppingClock = SteppingClock(Instant.parse("2026-10-01T00:00:00Z"))
    }

    @Autowired
    private lateinit var service: ConversationService

    @Autowired
    private lateinit var clock: SteppingClock

    @Autowired
    private lateinit var inFlight: InFlightTurns

    private val owner = "owner-a"
    private val stranger = "owner-b"

    @BeforeEach
    fun reset() {
        service.deleteAll(owner)
        service.deleteAll(stranger)
    }

    @Test
    fun `should record user and assistant turns in order and reload them`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("How is my portfolio?", deepThink = true))
        service.appendAssistant(owner, id, "Up 2% this week.", error = null)

        val detail = service.get(owner, id)

        assertThat(detail.messages.map { it.role }).containsExactly("user", "assistant")
        assertThat(detail.messages.map { it.content }).containsExactly("How is my portfolio?", "Up 2% this week.")
        assertThat(detail.messages.first().deepThink).isTrue()
    }

    @Test
    fun `should start every conversation as a plain chat`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("How is my portfolio?"))

        assertThat(service.get(owner, id).kind).isEqualTo(ConversationKind.CHAT)
        assertThat(service.list(owner, 0, 10).single().kind).isEqualTo(ConversationKind.CHAT)
    }

    @Test
    fun `should open a turn with the history before it and seed a title only until the first answer`() {
        val id = service.create(owner).id

        val first =
            service.openTurn(
                owner,
                id,
                Question("Full prompt", label = "Index funds"),
                ConversationKind.CHAT,
                6
            )
        service.appendAssistant(owner, id, "They track an index.", null)
        val second = service.openTurn(owner, id, Question("And bonds?"), ConversationKind.CHAT, 6)

        assertThat(first.history).isEmpty()
        assertThat(first.titleSeed).isEqualTo(TitleSeed("Index funds", listOf("Index funds")))
        assertThat(second.history.map { it.content }).containsExactly("Full prompt", "They track an index.")
        assertThat(second.titleSeed).isNull()
    }

    @Test
    fun `should not seed a title for a hidden conversation`() {
        val id = service.create(owner, hidden = true).id

        val opened = service.openTurn(owner, id, Question("How is my portfolio?"), ConversationKind.CHAT, 6)

        assertThat(opened.titleSeed).isNull()
        assertThat(service.get(owner, id).messages.map { it.content }).containsExactly("How is my portfolio?")
    }

    @Test
    fun `should take its kind from the first turn and keep it`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("Analyse my holding"), kind = ConversationKind.HOLDING_ANALYSIS)
        service.appendAssistant(owner, id, "Sized at a tenth of the book.", null)
        service.appendUser(owner, id, Question("And the review?"), kind = ConversationKind.ASSET_REVIEW)

        assertThat(service.get(owner, id).kind).isEqualTo(ConversationKind.HOLDING_ANALYSIS)
        assertThat(service.stateOf(owner, id).kind).isEqualTo(ConversationKind.HOLDING_ANALYSIS)
        assertThat(service.list(owner, 0, 10).single().kind).isEqualTo(ConversationKind.HOLDING_ANALYSIS)
    }

    @Autowired
    private lateinit var jdbc: org.springframework.jdbc.core.JdbcTemplate

    @Test
    fun `should refuse a kind the schema does not know`() {
        val insert =
            "insert into conversation (id, owner_id, title, created_at, updated_at, hidden, kind) " +
                "values ('bogus-kind', ?, '', current_timestamp, current_timestamp, false, 'BOGUS')"

        assertThatThrownBy { jdbc.update(insert, owner) }
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException::class.java)
    }

    @Test
    fun `should not turn a chat into a review after its first turn`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("What is an ETF?"))
        service.appendUser(owner, id, Question("Review it"), kind = ConversationKind.ASSET_REVIEW)

        assertThat(service.stateOf(owner, id).kind).isEqualTo(ConversationKind.CHAT)
    }

    @Test
    fun `should title a conversation provisionally from its first user message`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("In one sentence, what is an index fund?"))
        service.appendUser(owner, id, Question("And bonds?"))

        assertThat(service.titleOf(owner, id)).isEqualTo("Index fund")
    }

    @Test
    fun `should keep a display label beside the question and title the conversation from it`() {
        val id = service.create(owner).id
        val canned = "Summarise recent news and sentiment for NATO on LSE. Respond in markdown with sections..."
        service.appendUser(owner, id, Question(canned, false, label = "News & Sentiment — NATO"))

        val first = service.get(owner, id).messages.first()
        assertThat(first.content).isEqualTo(canned)
        assertThat(first.label).isEqualTo("News & Sentiment — NATO")
        assertThat(service.titleOf(owner, id)).isEqualTo("News Sentiment NATO")
    }

    @Test
    fun `should clip an over-long label rather than fail the turn`() {
        val id = service.create(owner).id

        service.appendUser(owner, id, Question("prompt", false, label = "L".repeat(500)))

        assertThat(
            service
                .get(owner, id)
                .messages
                .first()
                .label
        ).hasSize(ConversationService.LABEL_MAX_CHARS)
    }

    @Test
    fun `should replace a provisional title with a generated one`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("In one sentence, what is an index fund?"))

        val applied =
            service.applyGeneratedTitle(
                owner,
                id,
                provisional = "Index fund",
                generated = "Index Fund Basics"
            )

        assertThat(applied).isTrue()
        assertThat(service.titleOf(owner, id)).isEqualTo("Index Fund Basics")
    }

    @Test
    fun `should never overwrite a title the user chose`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("In one sentence, what is an index fund?"))
        service.rename(owner, id, "My notes")

        val applied =
            service.applyGeneratedTitle(
                owner,
                id,
                provisional = "Index fund",
                generated = "Index Fund Basics"
            )

        assertThat(applied).isFalse()
        assertThat(service.titleOf(owner, id)).isEqualTo("My notes")
    }

    @Test
    fun `should list only the caller's conversations, most recently active first`() {
        val older = service.create(owner).id
        service.appendUser(owner, older, Question("first"))
        clock.advance(Duration.ofMinutes(5))
        val newer = service.create(owner).id
        service.appendUser(owner, newer, Question("second"))
        clock.advance(Duration.ofMinutes(5))
        service.appendUser(owner, older, Question("older one, revisited"))
        service.create(stranger)

        val listed = service.list(owner, page = 0, size = 20)

        assertThat(listed.map { it.id }).containsExactly(older, newer)
    }

    @Test
    fun `should keep a hidden conversation out of the list but return it by id`() {
        val id = service.create(owner, hidden = true).id
        service.appendUser(owner, id, Question("Review VOO"))
        service.appendAssistant(owner, id, "An S&P 500 ETF.", null)

        assertThat(service.list(owner, page = 0, size = 20)).isEmpty()
        assertThat(service.get(owner, id).messages.map { it.content }).containsExactly("Review VOO", "An S&P 500 ETF.")
    }

    @Test
    fun `should not list a conversation that never received a message`() {
        service.create(owner)

        assertThat(service.list(owner, page = 0, size = 20)).isEmpty()
    }

    @Test
    fun `should hide another owner's conversation as not found`() {
        val id = service.create(owner).id

        assertThatThrownBy { service.get(stranger, id) }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { service.appendUser(stranger, id, Question("hi")) }
            .isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { service.delete(stranger, id) }.isInstanceOf(NotFoundException::class.java)
        assertThat(service.get(owner, id).id).isEqualTo(id)
    }

    @Test
    fun `should replay history without failed answers, capped to the trailing turns`() {
        val id = service.create(owner).id
        repeat(4) { i ->
            service.appendUser(owner, id, Question("q$i"))
            service.appendAssistant(owner, id, "a$i", error = null)
        }
        service.appendUser(owner, id, Question("q4"))
        service.appendAssistant(owner, id, "", error = "provider-rate")

        val history = service.openTurn(owner, id, Question("next"), ConversationKind.CHAT, maxHistory = 4).history

        // The unanswered q4 stays, so "try again" still has its question.
        assertThat(history).containsExactly(
            ChatTurn("assistant", "a2"),
            ChatTurn("user", "q3"),
            ChatTurn("assistant", "a3"),
            ChatTurn("user", "q4")
        )
    }

    @Test
    fun `should keep a failed turn's error code for display after reload`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("q"))
        service.appendAssistant(owner, id, "", error = "provider-quota")

        assertThat(
            service
                .get(owner, id)
                .messages
                .last()
                .error
        ).isEqualTo("provider-quota")
    }

    @Test
    fun `should rename a conversation`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("q"))

        service.rename(owner, id, "Runway questions")

        assertThat(service.get(owner, id).title).isEqualTo("Runway questions")
    }

    @Test
    fun `should reject a blank title`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("q"))

        assertThatThrownBy { service.rename(owner, id, "   ") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `should store a generated title without surrounding space`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("In one sentence, what is an index fund?"))

        service.applyGeneratedTitle(owner, id, provisional = "Index fund", generated = "  Index Fund Basics  ")

        assertThat(service.titleOf(owner, id)).isEqualTo("Index Fund Basics")
    }

    @Test
    fun `should delete a conversation and its messages`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("q"))

        service.delete(owner, id)

        assertThatThrownBy { service.get(owner, id) }.isInstanceOf(NotFoundException::class.java)
        assertThat(service.list(owner, 0, 20)).isEmpty()
    }

    @Test
    fun `should delete every conversation an owner has, leaving others untouched`() {
        repeat(2) { service.appendUser(owner, service.create(owner).id, Question("q")) }
        val kept = service.create(stranger).id
        service.appendUser(stranger, kept, Question("q"))

        val deleted = service.deleteAll(owner)

        assertThat(deleted).isEqualTo(2)
        assertThat(service.list(owner, 0, 20)).isEmpty()
        assertThat(service.list(stranger, 0, 20).map { it.id }).containsExactly(kept)
    }

    @Test
    fun `should purge conversations idle beyond the retention window`() {
        val stale = service.create(owner).id
        service.appendUser(owner, stale, Question("old"))
        clock.advance(Duration.ofDays(91))
        val fresh = service.create(owner).id
        service.appendUser(owner, fresh, Question("new"))

        val purged = service.purgeIdleLongerThan(Duration.ofDays(90))

        assertThat(purged).isEqualTo(1)
        assertThat(service.list(owner, 0, 20).map { it.id }).containsExactly(fresh)
        assertThatThrownBy { service.get(owner, stale) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `get should report pending only while a turn is in flight`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("How is my portfolio?"))

        inFlight.begin(id)
        assertThat(service.get(owner, id).pending).isTrue()

        inFlight.end(id)
        assertThat(service.get(owner, id).pending).isFalse()
    }

    @Test
    fun `get should stay pending until the last overlapping turn ends`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, Question("How is my portfolio?"))

        inFlight.begin(id)
        inFlight.begin(id)
        inFlight.end(id)
        assertThat(service.get(owner, id).pending).isTrue()

        inFlight.end(id)
        assertThat(service.get(owner, id).pending).isFalse()
    }

    @Test
    fun `should return the owner's conversation for a subject instead of a second one`() {
        val first = service.findOrCreate(owner, "portfolio-review|A|DBS,USV")
        service.appendUser(owner, first.id, Question("review"))

        val again = service.findOrCreate(owner, "portfolio-review|A|DBS,USV")

        assertThat(again.id).isEqualTo(first.id)
    }

    @Test
    fun `should keep a different selection of portfolios in its own conversation`() {
        val first = service.findOrCreate(owner, "portfolio-review|A|DBS,USV")

        val other = service.findOrCreate(owner, "portfolio-review|A|DBS")

        assertThat(other.id).isNotEqualTo(first.id)
    }

    @Test
    fun `should never share a subject's conversation between owners`() {
        val mine = service.findOrCreate(owner, "portfolio-review|A|DBS")

        val theirs = service.findOrCreate(stranger, "portfolio-review|A|DBS")

        assertThat(theirs.id).isNotEqualTo(mine.id)
    }

    @Test
    fun `should leave a free chat outside any subject`() {
        val chat = service.create(owner)
        service.appendUser(owner, chat.id, Question("hello"))

        val review = service.findOrCreate(owner, "portfolio-review|A|DBS")

        assertThat(review.id).isNotEqualTo(chat.id)
    }
}