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
            classes = [ConversationService::class]
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
        service.appendUser(owner, id, "How is my portfolio?", deepThink = true)
        service.appendAssistant(owner, id, "Up 2% this week.", error = null)

        val detail = service.get(owner, id)

        assertThat(detail.messages.map { it.role }).containsExactly("user", "assistant")
        assertThat(detail.messages.map { it.content }).containsExactly("How is my portfolio?", "Up 2% this week.")
        assertThat(detail.messages.first().deepThink).isTrue()
    }

    @Test
    fun `should title a conversation provisionally from its first user message`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, "In one sentence, what is an index fund?", false)
        service.appendUser(owner, id, "And bonds?", false)

        assertThat(service.titleOf(owner, id)).isEqualTo("Index fund")
    }

    @Test
    fun `should keep a display label beside the question and title the conversation from it`() {
        val id = service.create(owner).id
        val canned = "Summarise recent news and sentiment for NATO on LSE. Respond in markdown with sections..."
        service.appendUser(owner, id, canned, false, label = "News & Sentiment — NATO")

        val first = service.get(owner, id).messages.first()
        assertThat(first.content).isEqualTo(canned)
        assertThat(first.label).isEqualTo("News & Sentiment — NATO")
        assertThat(service.titleOf(owner, id)).isEqualTo("News Sentiment NATO")
    }

    @Test
    fun `should replace a provisional title with a generated one`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, "In one sentence, what is an index fund?", false)

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
        service.appendUser(owner, id, "In one sentence, what is an index fund?", false)
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
        service.appendUser(owner, older, "first", false)
        clock.advance(Duration.ofMinutes(5))
        val newer = service.create(owner).id
        service.appendUser(owner, newer, "second", false)
        clock.advance(Duration.ofMinutes(5))
        service.appendUser(owner, older, "older one, revisited", false)
        service.create(stranger)

        val listed = service.list(owner, page = 0, size = 20)

        assertThat(listed.map { it.id }).containsExactly(older, newer)
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
        assertThatThrownBy { service.appendUser(stranger, id, "hi", false) }
            .isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { service.delete(stranger, id) }.isInstanceOf(NotFoundException::class.java)
        assertThat(service.get(owner, id).id).isEqualTo(id)
    }

    @Test
    fun `should replay history without failed answers, capped to the trailing turns`() {
        val id = service.create(owner).id
        repeat(4) { i ->
            service.appendUser(owner, id, "q$i", false)
            service.appendAssistant(owner, id, "a$i", error = null)
        }
        service.appendUser(owner, id, "q4", false)
        service.appendAssistant(owner, id, "", error = "provider-rate")

        val history = service.history(owner, id, maxTurns = 4)

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
        service.appendUser(owner, id, "q", false)
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
        service.appendUser(owner, id, "q", false)

        service.rename(owner, id, "Runway questions")

        assertThat(service.get(owner, id).title).isEqualTo("Runway questions")
    }

    @Test
    fun `should reject a blank title`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, "q", false)

        assertThatThrownBy { service.rename(owner, id, "   ") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `should store a generated title without surrounding space`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, "In one sentence, what is an index fund?", false)

        service.applyGeneratedTitle(owner, id, provisional = "Index fund", generated = "  Index Fund Basics  ")

        assertThat(service.titleOf(owner, id)).isEqualTo("Index Fund Basics")
    }

    @Test
    fun `should delete a conversation and its messages`() {
        val id = service.create(owner).id
        service.appendUser(owner, id, "q", false)

        service.delete(owner, id)

        assertThatThrownBy { service.get(owner, id) }.isInstanceOf(NotFoundException::class.java)
        assertThat(service.list(owner, 0, 20)).isEmpty()
    }

    @Test
    fun `should delete every conversation an owner has, leaving others untouched`() {
        repeat(2) { service.appendUser(owner, service.create(owner).id, "q", false) }
        val kept = service.create(stranger).id
        service.appendUser(stranger, kept, "q", false)

        val deleted = service.deleteAll(owner)

        assertThat(deleted).isEqualTo(2)
        assertThat(service.list(owner, 0, 20)).isEmpty()
        assertThat(service.list(stranger, 0, 20).map { it.id }).containsExactly(kept)
    }

    @Test
    fun `should purge conversations idle beyond the retention window`() {
        val stale = service.create(owner).id
        service.appendUser(owner, stale, "old", false)
        clock.advance(Duration.ofDays(91))
        val fresh = service.create(owner).id
        service.appendUser(owner, fresh, "new", false)

        val purged = service.purgeIdleLongerThan(Duration.ofDays(90))

        assertThat(purged).isEqualTo(1)
        assertThat(service.list(owner, 0, 20).map { it.id }).containsExactly(fresh)
        assertThatThrownBy { service.get(owner, stale) }.isInstanceOf(NotFoundException::class.java)
    }
}