package com.beancounter.agent.conversation

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * A subject's one conversation. [ConversationAdmin.findOrCreate] commits on
 * its own, as the controller calls it outside any transaction, so these tests
 * run without one too and clean up after themselves.
 */
@DataJpaTest(
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [ConversationAdmin::class, TurnLog::class, InFlightTurns::class]
        )
    ],
    properties = [
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:agent-subjects;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
    ]
)
@Import(SteppingClockConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConversationSubjectTest {
    @Autowired
    private lateinit var admin: ConversationAdmin

    @Autowired
    private lateinit var turnLog: TurnLog

    private val owner = "owner-subject"
    private val stranger = "stranger-subject"

    @AfterEach
    fun reset() {
        admin.deleteAll(owner)
        admin.deleteAll(stranger)
    }

    @Test
    fun `should return the owner's conversation for a subject instead of a second one`() {
        val first = admin.findOrCreate(owner, "portfolio-review|A|DBS,USV")
        turnLog.appendUser(owner, first.id, Question("review"))

        val again = admin.findOrCreate(owner, "portfolio-review|A|DBS,USV")

        assertThat(again.id).isEqualTo(first.id)
    }

    @Test
    fun `should keep a different selection of portfolios in its own conversation`() {
        val first = admin.findOrCreate(owner, "portfolio-review|A|DBS,USV")

        val other = admin.findOrCreate(owner, "portfolio-review|A|DBS")

        assertThat(other.id).isNotEqualTo(first.id)
    }

    @Test
    fun `should never share a subject's conversation between owners`() {
        val mine = admin.findOrCreate(owner, "portfolio-review|A|DBS")

        val theirs = admin.findOrCreate(stranger, "portfolio-review|A|DBS")

        assertThat(theirs.id).isNotEqualTo(mine.id)
    }

    @Test
    fun `should leave a free chat outside any subject`() {
        val chat = admin.create(owner)
        turnLog.appendUser(owner, chat.id, Question("hello"))

        val review = admin.findOrCreate(owner, "portfolio-review|A|DBS")

        assertThat(review.id).isNotEqualTo(chat.id)
    }

    @Test
    fun `should surface an insert failure that is not a lost race`() {
        val tooLong = "s".repeat(SUBJECT_COLUMN_CHARS + 1)

        assertThatThrownBy { admin.findOrCreate(owner, tooLong) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private companion object {
        const val SUBJECT_COLUMN_CHARS = 512
    }
}