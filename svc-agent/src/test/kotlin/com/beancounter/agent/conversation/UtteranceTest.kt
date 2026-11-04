package com.beancounter.agent.conversation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UtteranceTest {
    @Test
    fun `should record a question as the user's side with its tier and label`() {
        val asked = Utterance.asked(Question("prompt", deepThink = true, label = "News & Sentiment"))

        assertThat(asked).isEqualTo(
            Utterance(Utterance.ROLE_USER, "prompt", deepThink = true, error = null, label = "News & Sentiment")
        )
    }

    @Test
    fun `should clip an over-long label to the column`() {
        val asked = Utterance.asked(Question("prompt", label = "L".repeat(500)))

        assertThat(asked.label).hasSize(Utterance.LABEL_MAX_CHARS)
        assertThat(asked.content).isEqualTo("prompt")
    }

    @Test
    fun `should carry no label when the question has none`() {
        assertThat(Utterance.asked(Question("prompt")).label).isNull()
    }
}