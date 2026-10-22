package com.beancounter.agent.quota

import com.beancounter.agent.conversation.TurnRecorder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * [QuotaRecorder] counts an answered turn exactly once, even when recording
 * it fails, and never counts a failed turn.
 */
class QuotaRecorderTest {
    private val spent = mutableListOf<String>()
    private val quota =
        object : TurnQuota {
            override fun allows(ownerId: String) = true

            override fun spend(ownerId: String) {
                spent += ownerId
            }

            override fun disabled(ownerId: String) = false
        }

    private fun recorder(delegate: TurnRecorder) = QuotaRecorder(delegate, quota, "owner")

    private val quiet = TurnRecorder.NONE

    @Test
    fun `an answered turn should be counted once`() {
        recorder(quiet).answered("An answer.")

        assertThat(spent).containsExactly("owner")
    }

    @Test
    fun `an answered turn should be counted even when recording it fails`() {
        val broken =
            object : TurnRecorder {
                override fun answered(content: String): Unit = throw IllegalStateException("db down")

                override fun failed(code: String) = Unit
            }

        assertThatThrownBy { recorder(broken).answered("An answer.") }.isInstanceOf(IllegalStateException::class.java)
        assertThat(spent).containsExactly("owner")
    }

    @Test
    fun `a failed turn should not be counted`() {
        recorder(quiet).failed("agent-error")

        assertThat(spent).isEmpty()
    }
}