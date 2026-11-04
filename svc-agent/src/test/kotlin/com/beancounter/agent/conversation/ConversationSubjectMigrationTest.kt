package com.beancounter.agent.conversation

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils

/**
 * V9 runs against conversations a findOrCreate race already duplicated: the
 * newest keeps the subject, the others stay as ordinary threads with their
 * turns, and a second run changes nothing.
 */
class ConversationSubjectMigrationTest {
    private val dataSource =
        SingleConnectionDataSource(
            "jdbc:h2:mem:agent-subject-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            true
        )
    private val jdbc = JdbcTemplate(dataSource)

    private fun flyway(target: String) =
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(target)
            .load()

    private fun insert(
        id: String,
        subject: String?,
        updatedAt: String
    ) {
        jdbc.update(
            "insert into conversation (id, owner_id, title, created_at, updated_at, hidden, kind, subject) " +
                "values (?, 'owner', '', timestamp with time zone '$updatedAt', " +
                "timestamp with time zone '$updatedAt', false, 'CHAT', ?)",
            id,
            subject
        )
    }

    private fun subjects(): Map<String, String?> =
        jdbc
            .queryForList("select id, subject from conversation order by id")
            .associate { it["id"] as String to it["subject"] as String? }

    @Test
    fun `should keep the newest conversation as the subject's and its duplicates as plain threads`() {
        flyway("8").migrate()
        insert("older", "review", "2026-10-01T00:00:00Z")
        insert("newest", "review", "2026-10-03T00:00:00Z")
        insert("middle", "review", "2026-10-02T00:00:00Z")
        insert("tie-a", "tied", "2026-10-01T00:00:00Z")
        insert("tie-b", "tied", "2026-10-01T00:00:00Z")
        insert("plain", null, "2026-10-01T00:00:00Z")
        jdbc.update(
            "insert into conversation_message (id, conversation_id, seq, role, content, deep_think, created_at) " +
                "values ('turn', 'older', 0, 'user', 'q', false, current_timestamp)"
        )

        flyway("latest").migrate()
        ScriptUtils.executeSqlScript(
            dataSource.connection,
            ClassPathResource("db/migration/V9__conversation_subject_unique.sql")
        )

        assertThat(subjects()).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "older" to null,
                "middle" to null,
                "newest" to "review",
                "tie-a" to null,
                "tie-b" to "tied",
                "plain" to null
            )
        )
        assertThat(jdbc.queryForObject("select conversation_id from conversation_message", String::class.java))
            .isEqualTo("older")
        insert("plain-2", null, "2026-10-01T00:00:00Z")
        assertThatThrownBy { insert("again", "review", "2026-10-04T00:00:00Z") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }
}