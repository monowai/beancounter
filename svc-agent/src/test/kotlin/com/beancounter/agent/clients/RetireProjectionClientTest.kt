package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.endsWith
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

private const val PROJECTION_BEARER = "Bearer projection-token"

/** Drives [RetireProjectionClient] against a [MockRestServiceServer]: the caller's token, path and body. */
class RetireProjectionClientTest {
    private val tokenService = mock<TokenService> { on { bearerToken } doReturn PROJECTION_BEARER }

    private fun clientWithServer(): Pair<RetireProjectionClient, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl("http://retire")
        val server = MockRestServiceServer.bindTo(builder).build()
        return RetireProjectionClient(builder.build(), tokenService) to server
    }

    private fun MockRestServiceServer.expectPost(
        path: String,
        body: String
    ) {
        expect(method(HttpMethod.POST))
            .andExpect(requestTo(endsWith(path)))
            .andExpect(header("Authorization", PROJECTION_BEARER))
            .andExpect(content().json(body, true))
            .andRespond(withSuccess("""{"planId":"phase-1"}""", MediaType.APPLICATION_JSON))
    }

    @Test
    fun `a phase's monte carlo posts its iterations and display currency as the caller`() {
        val (client, server) = clientWithServer()
        server.expectPost("/projection/plans/phase-1/monte-carlo", """{"iterations":500,"displayCurrency":"NZD"}""")

        val result = client.runMonteCarlo("phase-1", 500, "NZD")

        assertThat(result).containsEntry("planId", "phase-1")
        server.verify()
    }

    @Test
    fun `a projection without a display currency posts an empty body`() {
        val (client, server) = clientWithServer()
        server.expectPost("/projection/plans/phase-1", "{}")

        client.runProjection("phase-1")

        server.verify()
    }

    @Test
    fun `a composite projection posts its phases`() {
        val (client, server) = clientWithServer()
        server.expectPost(
            "/composite/projection",
            """{"displayCurrency":"NZD","phases":[{"planId":"phase-1","fromAge":55}]}"""
        )

        client.runCompositeProjection(listOf(CompositePhaseInput("phase-1", 55)), "NZD")

        server.verify()
    }
}