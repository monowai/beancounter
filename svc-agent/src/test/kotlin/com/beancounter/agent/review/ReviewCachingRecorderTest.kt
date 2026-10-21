package com.beancounter.agent.review

import com.beancounter.agent.conversation.TurnRecorder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ReviewCachingRecorderTest {
    private val key = AssetReviewKey("NASDAQ", "NATO", LocalDate.of(2026, 10, 1), "smart")
    private val stored = mutableListOf<String>()
    private val cache =
        object : AssetReviewCache {
            override val enabled = true

            override fun find(key: AssetReviewKey): String? = null

            override fun store(
                key: AssetReviewKey,
                content: String
            ) {
                stored += content
            }
        }

    private fun recorder(answered: (String) -> Unit) =
        ReviewCachingRecorder(
            object : TurnRecorder {
                override fun answered(content: String) = answered(content)

                override fun failed(code: String) = Unit
            },
            cache,
            key
        )

    @Test
    fun `should cache the answer once the turn is recorded`() {
        recorder {}.answered("NATO looks steady.")

        assertThat(stored).containsExactly("NATO looks steady.")
    }

    @Test
    fun `should cache nothing when recording the turn fails`() {
        assertThatThrownBy { recorder { throw IllegalStateException("db down") }.answered("NATO looks steady.") }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(stored).isEmpty()
    }

    @Test
    fun `should cache nothing for a failed turn`() {
        recorder {}.failed("provider-rate")

        assertThat(stored).isEmpty()
    }
}