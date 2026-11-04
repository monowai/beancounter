package com.beancounter.agent.conversation

import com.beancounter.agent.ChatTurn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import

/** [TurnLog] against a real database: the turns answering a question reads and writes. */
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
class TurnLogTest {
    @Autowired
    private lateinit var admin: ConversationAdmin

    @Autowired
    private lateinit var turnLog: TurnLog

    private val owner = "owner-a"
    private val stranger = "owner-b"

    @BeforeEach
    fun reset() {
        admin.deleteAll(owner)
        admin.deleteAll(stranger)
    }

    @Test
    fun `should record user and assistant turns in order and reload them`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("How is my portfolio?", deepThink = true))
        turnLog.appendAssistant(owner, id, "Up 2% this week.", error = null)

        val detail = admin.get(owner, id)

        assertThat(detail.messages.map { it.role }).containsExactly("user", "assistant")
        assertThat(detail.messages.map { it.content }).containsExactly("How is my portfolio?", "Up 2% this week.")
        assertThat(detail.messages.first().deepThink).isTrue()
    }

    @Test
    fun `should start every conversation as a plain chat`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("How is my portfolio?"))

        assertThat(admin.get(owner, id).kind).isEqualTo(ConversationKind.CHAT)
        assertThat(admin.list(owner, 0, 10).single().kind).isEqualTo(ConversationKind.CHAT)
    }

    @Test
    fun `should open a turn with the history before it and seed a title only until the first answer`() {
        val id = admin.create(owner).id

        val first =
            turnLog.openTurn(
                owner,
                id,
                Question("Full prompt", label = "Index funds"),
                ConversationKind.CHAT,
                6
            )
        turnLog.appendAssistant(owner, id, "They track an index.", null)
        val second = turnLog.openTurn(owner, id, Question("And bonds?"), ConversationKind.CHAT, 6)

        assertThat(first.history).isEmpty()
        assertThat(first.titleSeed).isEqualTo(TitleSeed("Index funds", listOf("Index funds")))
        assertThat(second.history.map { it.content }).containsExactly("Full prompt", "They track an index.")
        assertThat(second.titleSeed).isNull()
    }

    @Test
    fun `should not seed a title for a hidden conversation`() {
        val id = admin.create(owner, hidden = true).id

        val opened = turnLog.openTurn(owner, id, Question("How is my portfolio?"), ConversationKind.CHAT, 6)

        assertThat(opened.titleSeed).isNull()
        assertThat(admin.get(owner, id).messages.map { it.content }).containsExactly("How is my portfolio?")
    }

    @Test
    fun `should take its kind from the first turn and keep it`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("Analyse my holding"), kind = ConversationKind.HOLDING_ANALYSIS)
        turnLog.appendAssistant(owner, id, "Sized at a tenth of the book.", null)
        turnLog.appendUser(owner, id, Question("And the review?"), kind = ConversationKind.ASSET_REVIEW)

        assertThat(admin.get(owner, id).kind).isEqualTo(ConversationKind.HOLDING_ANALYSIS)
        assertThat(turnLog.stateOf(owner, id).kind).isEqualTo(ConversationKind.HOLDING_ANALYSIS)
        assertThat(admin.list(owner, 0, 10).single().kind).isEqualTo(ConversationKind.HOLDING_ANALYSIS)
    }

    @Test
    fun `should not turn a chat into a review after its first turn`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("What is an ETF?"))
        turnLog.appendUser(owner, id, Question("Review it"), kind = ConversationKind.ASSET_REVIEW)

        assertThat(turnLog.stateOf(owner, id).kind).isEqualTo(ConversationKind.CHAT)
    }

    @Test
    fun `should title a conversation provisionally from its first user message`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("In one sentence, what is an index fund?"))
        turnLog.appendUser(owner, id, Question("And bonds?"))

        assertThat(turnLog.titleOf(owner, id)).isEqualTo("Index fund")
    }

    @Test
    fun `should keep a display label beside the question and title the conversation from it`() {
        val id = admin.create(owner).id
        val canned = "Summarise recent news and sentiment for NATO on LSE. Respond in markdown with sections..."
        turnLog.appendUser(owner, id, Question(canned, false, label = "News & Sentiment — NATO"))

        val first = admin.get(owner, id).messages.first()
        assertThat(first.content).isEqualTo(canned)
        assertThat(first.label).isEqualTo("News & Sentiment — NATO")
        assertThat(turnLog.titleOf(owner, id)).isEqualTo("News Sentiment NATO")
    }

    @Test
    fun `should clip an over-long label rather than fail the turn`() {
        val id = admin.create(owner).id

        turnLog.appendUser(owner, id, Question("prompt", false, label = "L".repeat(500)))

        assertThat(
            admin
                .get(owner, id)
                .messages
                .first()
                .label
        ).hasSize(Utterance.LABEL_MAX_CHARS)
    }

    @Test
    fun `should replace a provisional title with a generated one`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("In one sentence, what is an index fund?"))

        val applied =
            turnLog.applyGeneratedTitle(
                owner,
                id,
                provisional = "Index fund",
                generated = "Index Fund Basics"
            )

        assertThat(applied).isTrue()
        assertThat(turnLog.titleOf(owner, id)).isEqualTo("Index Fund Basics")
    }

    @Test
    fun `should never overwrite a title the user chose`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("In one sentence, what is an index fund?"))
        admin.rename(owner, id, "My notes")

        val applied =
            turnLog.applyGeneratedTitle(
                owner,
                id,
                provisional = "Index fund",
                generated = "Index Fund Basics"
            )

        assertThat(applied).isFalse()
        assertThat(turnLog.titleOf(owner, id)).isEqualTo("My notes")
    }

    @Test
    fun `should replay history without failed answers, capped to the trailing turns`() {
        val id = admin.create(owner).id
        repeat(4) { i ->
            turnLog.appendUser(owner, id, Question("q$i"))
            turnLog.appendAssistant(owner, id, "a$i", error = null)
        }
        turnLog.appendUser(owner, id, Question("q4"))
        turnLog.appendAssistant(owner, id, "", error = "provider-rate")

        val history = turnLog.openTurn(owner, id, Question("next"), ConversationKind.CHAT, maxHistory = 4).history

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
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("q"))
        turnLog.appendAssistant(owner, id, "", error = "provider-quota")

        assertThat(
            admin
                .get(owner, id)
                .messages
                .last()
                .error
        ).isEqualTo("provider-quota")
    }

    @Test
    fun `should store a generated title without surrounding space`() {
        val id = admin.create(owner).id
        turnLog.appendUser(owner, id, Question("In one sentence, what is an index fund?"))

        turnLog.applyGeneratedTitle(owner, id, provisional = "Index fund", generated = "  Index Fund Basics  ")

        assertThat(turnLog.titleOf(owner, id)).isEqualTo("Index Fund Basics")
    }
}