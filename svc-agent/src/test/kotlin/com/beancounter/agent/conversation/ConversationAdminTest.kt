package com.beancounter.agent.conversation

import com.beancounter.common.exception.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import java.time.Duration

/**
 * [ConversationAdmin] against a real database. Flyway runs the shipped
 * migrations and Hibernate validates the entities against them, so a
 * migration that drifts from the entity mapping fails here rather than in a
 * CrashLoopBackOff on kauri.
 */
@DataJpaTest(
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [ConversationAdmin::class, TurnLog::class, InFlightTurns::class]
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
@Import(SteppingClockConfig::class)
class ConversationAdminTest {
    @Autowired
    private lateinit var admin: ConversationAdmin

    @Autowired
    private lateinit var turnLog: TurnLog

    @Autowired
    private lateinit var clock: SteppingClock

    @Autowired
    private lateinit var inFlight: InFlightTurns

    @Autowired
    private lateinit var conversations: ConversationRepository

    @Autowired
    private lateinit var messages: ConversationMessageRepository

    private val owner = "owner-a"
    private val stranger = "owner-b"

    @BeforeEach
    fun reset() {
        admin.deleteAll(owner)
        admin.deleteAll(stranger)
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
    fun `should list only the caller's conversations, most recently active first`() {
        val older = admin.create(owner).id
        turnLog.appendUser(owner, older, Question("first"))
        clock.advance(Duration.ofMinutes(5))
        val newer = admin.create(owner).id
        turnLog.appendUser(owner, newer, Question("second"))
        clock.advance(Duration.ofMinutes(5))
        turnLog.appendUser(owner, older, Question("older one, revisited"))
        admin.create(stranger)

        val listed = admin.list(owner, page = 0, size = 20)

        assertThat(listed.map { it.id }).containsExactly(older, newer)
    }

    @Test
    fun `should keep a hidden conversation out of the list but return it by id`() {
        val id = admin.create(owner, hidden = true).id
        turnLog.appendUser(owner, id, Question("Review VOO"))
        turnLog.appendAssistant(owner, id, "An S&P 500 ETF.", null)

        assertThat(admin.list(owner, page = 0, size = 20)).isEmpty()
        assertThat(admin.get(owner, id).messages.map { it.content }).containsExactly("Review VOO", "An S&P 500 ETF.")
    }

    @Test
    fun `should not list a conversation that never received a message`() {
        admin.create(owner)

        assertThat(admin.list(owner, page = 0, size = 20)).isEmpty()
    }

    @Test
    fun `should hide another owner's conversation as not found`() {
        val id = admin.create(owner).id

        assertThatThrownBy { admin.get(stranger, id) }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { turnLog.appendUser(stranger, id, Question("hi")) }
            .isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { admin.delete(stranger, id) }.isInstanceOf(NotFoundException::class.java)
        assertThat(admin.get(owner, id).id).isEqualTo(id)
    }

    @Test
    fun `should rename a conversation`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("q"))

        admin.rename(owner, id, "Runway questions")

        assertThat(admin.get(owner, id).title).isEqualTo("Runway questions")
    }

    @Test
    fun `should reject a blank title`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("q"))

        assertThatThrownBy { admin.rename(owner, id, "   ") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `should delete a conversation and its messages`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("q"))

        admin.delete(owner, id)

        assertThatThrownBy { admin.get(owner, id) }.isInstanceOf(NotFoundException::class.java)
        assertThat(admin.list(owner, 0, 20)).isEmpty()
    }

    @Test
    fun `should delete every conversation an owner has, leaving others untouched`() {
        repeat(2) { turnLog.appendUser(owner, admin.create(owner).id, Question("q")) }
        val kept = admin.create(stranger).id
        turnLog.appendUser(stranger, kept, Question("q"))

        val deleted = admin.deleteAll(owner)

        assertThat(deleted).isEqualTo(2)
        assertThat(admin.list(owner, 0, 20)).isEmpty()
        assertThat(admin.list(stranger, 0, 20).map { it.id }).containsExactly(kept)
    }

    @Test
    fun `should purge conversations idle beyond the retention window`() {
        val stale = admin.create(owner).id
        turnLog.appendUser(owner, stale, Question("old"))
        clock.advance(Duration.ofDays(91))
        val fresh = admin.create(owner).id
        turnLog.appendUser(owner, fresh, Question("new"))

        val purged = admin.purgeIdleLongerThan(Duration.ofDays(90))

        assertThat(purged).isEqualTo(1)
        assertThat(admin.list(owner, 0, 20).map { it.id }).containsExactly(fresh)
        assertThatThrownBy { admin.get(owner, stale) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `get should report pending only while a turn is in flight`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("How is my portfolio?"))

        inFlight.begin(id)
        assertThat(admin.get(owner, id).pending).isTrue()

        inFlight.end(id)
        assertThat(admin.get(owner, id).pending).isFalse()
    }

    @Test
    fun `get should stay pending until the last overlapping turn ends`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("How is my portfolio?"))

        inFlight.begin(id)
        inFlight.begin(id)
        inFlight.end(id)
        assertThat(admin.get(owner, id).pending).isTrue()

        inFlight.end(id)
        assertThat(admin.get(owner, id).pending).isFalse()
    }

    @Test
    fun `should delete nothing of a conversation when the statement names another owner`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("q"))

        assertThat(messages.deleteOfConversation(id, stranger)).isZero()
        assertThat(conversations.deleteByIdAndOwnerId(id, stranger)).isZero()

        assertThat(admin.get(owner, id).messages.map { it.content }).containsExactly("q")
    }
}