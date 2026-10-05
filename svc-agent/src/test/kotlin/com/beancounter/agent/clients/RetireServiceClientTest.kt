package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.endsWith
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * Drives [RetireServiceClient] against a [MockRestServiceServer] so the shape handed to the
 * model is pinned without standing up svc-retire. Mirrors [MacroClientTest].
 *
 * The vocabulary under test: svc-retire's `IndependencePlan` is the user's whole Plan (a
 * journey), and the ids inside its `phases` are phase ids — the `/plans` resources.
 */
class RetireServiceClientTest {
    private val tokenService = mock<TokenService> { on { bearerToken } doReturn BEARER }
    private val dateUtils = DateUtils()

    private fun clientWithServer(): Pair<RetireServiceClient, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        val server = MockRestServiceServer.bindTo(builder).build()
        return RetireServiceClient(builder.build(), tokenService, dateUtils) to server
    }

    private fun MockRestServiceServer.respondTo(
        path: String,
        json: String
    ) {
        expect(method(HttpMethod.GET))
            .andExpect(requestTo(endsWith(path)))
            .andExpect(header("Authorization", BEARER))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON))
    }

    @Suppress("UNCHECKED_CAST")
    private fun plansOf(result: Map<String, Any?>): List<Map<String, Any?>> = result["data"] as List<Map<String, Any?>>

    @Test
    fun `independence plans come back with the phase timeline as structured lists`() {
        val (client, server) = clientWithServer()
        server.respondTo("/independence-plans", PLANS_JSON)

        val plan = plansOf(client.listIndependencePlans()).first()

        // svc-retire stores both as JSON-encoded strings; the model gets them decoded.
        assertThat(plan["phases"]).isEqualTo(
            listOf(
                mapOf("planId" to "phase-1", "fromAge" to 55, "toAge" to 65),
                mapOf("planId" to "phase-2", "fromAge" to 65, "toAge" to null)
            )
        )
        assertThat(plan["excludedPlanIds"]).isEqualTo(listOf("phase-9"))
        server.verify()
    }

    @Test
    fun `independence plans keep identity, currency and assumption rates`() {
        val (client, server) = clientWithServer()
        server.respondTo("/independence-plans", PLANS_JSON)

        val plan = plansOf(client.listIndependencePlans()).first()

        assertThat(plan)
            .containsEntry("id", "journey-1")
            .containsEntry("name", "Own the house")
            .containsEntry("isPrimary", true)
            .containsEntry("displayCurrency", "NZD")
            .containsEntry("workScenarioId", "work-1")
            .containsEntry("inflationRate", 0.025)
            .containsEntry("equityReturnRate", 0.07)
    }

    @Test
    fun `independence plans drop the wealth definition and owner ids`() {
        val (client, server) = clientWithServer()
        server.respondTo("/independence-plans", PLANS_JSON)

        val plan = plansOf(client.listIndependencePlans()).first()

        assertThat(plan).doesNotContainKeys(
            "excludedPortfolioIds",
            "liquidatedPortfolioIds",
            "liquidationCostsPercent",
            "manualAssets",
            "ownerId",
            "systemUserId"
        )
    }

    @Test
    fun `independence plan with no stored timeline reports empty lists`() {
        val (client, server) = clientWithServer()
        server.respondTo("/independence-plans", PLANS_JSON)

        val plan = plansOf(client.listIndependencePlans())[1]

        assertThat(plan["id"]).isEqualTo("journey-2")
        assertThat(plan["phases"]).isEqualTo(emptyList<Any>())
        assertThat(plan["excludedPlanIds"]).isEqualTo(emptyList<Any>())
    }

    @Test
    fun `independence plan with an undecodable timeline is marked unreadable, not unset`() {
        val (client, server) = clientWithServer()
        server.respondTo(
            "/independence-plans",
            """{"data":[{"id":"journey-3","name":"Broken","isPrimary":false,"phases":"not json"}]}"""
        )

        val plan = plansOf(client.listIndependencePlans()).first()

        assertThat(plan["phases"]).isEqualTo(emptyList<Any>())
        assertThat(plan).containsEntry("phasesUnreadable", true)
    }

    @Test
    fun `independence plan with no stored timeline is not marked unreadable`() {
        val (client, server) = clientWithServer()
        server.respondTo(
            "/independence-plans",
            """{"data":[{"id":"journey-4","name":"Fresh","isPrimary":false,"phases":null}]}"""
        )

        val plan = plansOf(client.listIndependencePlans()).first()

        assertThat(plan).doesNotContainKey("phasesUnreadable")
    }

    @Test
    fun `independence settings are demographics plus a computed current age`() {
        val (client, server) = clientWithServer()
        server.respondTo("/settings", SETTINGS_JSON)

        val settings = client.getIndependenceSettings()

        // monthOfBirth 1 — the birthday has always passed, whatever month the test runs in.
        assertThat(settings)
            .containsEntry("currentAge", dateUtils.date.year - 1970)
            .containsEntry("targetIndependenceAge", 60)
            .containsEntry("lifeExpectancy", 92)
        server.verify()
    }

    @Test
    fun `independence settings carry no composite configuration`() {
        val (client, server) = clientWithServer()
        server.respondTo("/settings", SETTINGS_JSON)

        val settings = client.getIndependenceSettings()

        // The timeline lives per Plan on /independence-plans; /settings is the person.
        assertThat(settings.keys).noneMatch { it.startsWith("composite") }
    }

    private companion object {
        const val BASE_URL = "http://bc-retire"
        const val BEARER = "Bearer test"
        const val SETTINGS_JSON =
            """{"id":"s-1","ownerId":"owner-1","yearOfBirth":1970,"monthOfBirth":1,""" +
                """"targetIndependenceAge":60,"lifeExpectancy":92,"systemUserId":"su-1"}"""
        const val PLANS_JSON =
            """{"data":[{"id":"journey-1","ownerId":"owner-1","systemUserId":"su-1",""" +
                """"name":"Own the house","isPrimary":true,"displayCurrency":"NZD","workScenarioId":"work-1",""" +
                """"excludedPlanIds":"[\"phase-9\"]",""" +
                """"phases":"[{\"planId\":\"phase-1\",\"fromAge\":55,\"toAge\":65},""" +
                """{\"planId\":\"phase-2\",\"fromAge\":65,\"toAge\":null}]",""" +
                """"excludedPortfolioIds":"[\"pf-1\"]","liquidatedPortfolioIds":"[\"pf-2\"]",""" +
                """"liquidationCostsPercent":0.05,"manualAssets":"{\"CASH\":1000}",""" +
                """"cashReturnRate":0.015,"equityReturnRate":0.07,"housingReturnRate":0.04,""" +
                """"inflationRate":0.025,"feeRate":0.0,"investmentTaxRate":0.0},""" +
                """{"id":"journey-2","ownerId":"owner-1","name":"Rent","isPrimary":false,""" +
                """"displayCurrency":null,"excludedPlanIds":null,"phases":null}]}"""
    }
}