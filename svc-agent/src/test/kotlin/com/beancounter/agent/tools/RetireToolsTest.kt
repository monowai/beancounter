package com.beancounter.agent.tools

import com.beancounter.agent.clients.RetireServiceClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * [RetireTools] hands svc-retire responses to the LLM. The compute tools carry
 * year-by-year arrays big enough to exhaust the model's context window before it
 * answers (kauri: prompt_tokens=116608, finish_reason=length, no answer), so those
 * pass through [ProjectionCompactor] while the small read tools stay verbatim.
 */
internal class RetireToolsTest {
    private val client = mock<RetireServiceClient>()
    private val tools = RetireTools(client, ProjectionCompactor())

    private fun projectionWith(years: Int): Map<String, Any> =
        mapOf(
            "planId" to "plan-1",
            "depletionAge" to 87,
            "yearlyProjections" to
                (0 until years).map { i ->
                    mapOf(
                        "year" to 2026 + i,
                        "age" to 61 + i,
                        "endingBalance" to BigDecimal(900_000 - i * 1000)
                    )
                }
        )

    @Test
    fun `projection results are compacted before the LLM sees them`() {
        whenever(client.runProjection("plan-1", null)) doReturn projectionWith(40)

        val result = tools.runRetirementProjection("plan-1")

        // Headline metrics untouched — they are what the answer quotes.
        assertThat(result["depletionAge"]).isEqualTo(87)
        val table = result["yearlyProjections"] as Map<*, *>
        assertThat(table["cols"] as List<*>).contains("age", "endingBalance")
        assertThat(table["sampledFrom"]).isEqualTo(40)
    }

    @Test
    fun `scenario results are compacted`() {
        whenever(client.runScenarios("plan-1", "NZD")) doReturn
            mapOf("scenarios" to listOf(mapOf("name" to "base") + projectionWith(30)))

        val result = tools.runRetirementScenarios("plan-1", "NZD")

        val scenario = (result["scenarios"] as List<*>).first() as Map<*, *>
        assertThat((scenario["yearlyProjections"] as Map<*, *>)["sampledFrom"]).isEqualTo(30)
    }

    @Test
    fun `monte carlo results are compacted`() {
        whenever(client.runMonteCarlo("plan-1", 1000, null)) doReturn projectionWith(45)

        val result = tools.runRetirementMonteCarlo("plan-1")

        assertThat((result["yearlyProjections"] as Map<*, *>)["sampledFrom"]).isEqualTo(45)
    }

    @Test
    fun `plan reads pass through unchanged`() {
        // Small, and every field is one the model may need verbatim — compacting
        // here would cost clarity and save nothing.
        val plan = mapOf("planId" to "plan-1", "name" to "Retire at 61", "currency" to "NZD")
        whenever(client.getPlan("plan-1")) doReturn plan

        assertThat(tools.getRetirementPlan("plan-1")).isEqualTo(plan)
    }
}