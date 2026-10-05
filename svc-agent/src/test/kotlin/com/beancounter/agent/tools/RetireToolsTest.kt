package com.beancounter.agent.tools

import com.beancounter.agent.clients.RetireServiceClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.tool.annotation.Tool
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

    @Test
    fun `independence plans are listed straight from the client`() {
        val plans =
            mapOf<String, Any?>(
                "data" to
                    listOf(
                        mapOf(
                            "id" to "journey-1",
                            "isPrimary" to true,
                            "phases" to listOf(mapOf("planId" to "phase-1", "fromAge" to 55, "toAge" to null))
                        )
                    )
            )
        whenever(client.listIndependencePlans()) doReturn plans

        assertThat(tools.listIndependencePlans()).isEqualTo(plans)
    }

    @Test
    fun `listIndependencePlans is exposed to the model as a tool that explains the timeline`() {
        val tool = RetireTools::class.java.getMethod("listIndependencePlans").getAnnotation(Tool::class.java)

        assertThat(tool.description)
            .contains("phases", "planId", "isPrimary", "displayCurrency", "runCompositeRetirement")
    }

    @Test
    fun `settings description promises demographics only`() {
        // svc-retire moved the composite timeline off /settings onto each Plan; a
        // description that still advertises it sends the model looking for fields
        // that never arrive.
        assertThat(RetireTools.SETTINGS_DESC)
            .contains("currentAge", "targetIndependenceAge", "lifeExpectancy")
            .doesNotContainIgnoringCase("composite")
    }

    @Test
    fun `phase tools say they operate on a phase of an independence Plan`() {
        assertThat(listOf(RetireTools.LIST_DESC, RetireTools.GET_PLAN_DESC))
            .allSatisfy { desc -> assertThat(desc).contains("phase").contains("independence Plan") }
    }
}