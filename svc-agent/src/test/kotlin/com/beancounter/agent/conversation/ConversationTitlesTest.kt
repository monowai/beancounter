package com.beancounter.agent.conversation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConversationTitlesTest {
    @Test
    fun `should keep the key nouns of a question`() {
        assertThat(ConversationTitles.provisional("In one sentence, what is an index fund?")).isEqualTo("Index fund")
    }

    @Test
    fun `should drop pronouns, question words and filler`() {
        assertThat(ConversationTitles.provisional("Can you please tell me how my portfolio did last month?"))
            .isEqualTo("Portfolio did last month")
    }

    @Test
    fun `should drop answer-length instructions of any size`() {
        assertThat(
            ConversationTitles.provisional(
                "Can you please explain, in two sentences, how dollar cost averaging differs from lump sum investing?"
            )
        ).startsWith("Dollar cost averaging")
        assertThat(ConversationTitles.provisional("In 3 bullet points, what drives bond yields?"))
            .isEqualTo("Drives bond yields")
    }

    @Test
    fun `should keep tickers as written`() {
        assertThat(ConversationTitles.provisional("What is VOO?")).isEqualTo("VOO")
    }

    @Test
    fun `should cap a long question at a word boundary`() {
        val title =
            ConversationTitles.provisional(
                "What is my retirement runway if I stop working next year and markets fall sharply?"
            )

        assertThat(title).startsWith("Retirement runway stop working")
        assertThat(title).hasSizeLessThanOrEqualTo(ConversationTitles.PROVISIONAL_MAX_CHARS)
        assertThat(title).doesNotEndWith(" ")
    }

    @Test
    fun `should fall back to the question when nothing but filler remains`() {
        assertThat(ConversationTitles.provisional("  hi  ")).isEqualTo("hi")
    }
}