package com.beancounter.agent.client

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.web.client.RestClient

/**
 * An unreadable trades answer must fail the tool call, not read as "no buys or sells" —
 * the agent would otherwise tell the user they never traded the asset.
 */
class TrnClientTest {
    private val tokenService = mock<TokenService> { on { bearerToken } doReturn "Bearer test" }

    @Test
    fun `should fail rather than report no trades when svc-data returns no body`() {
        val builder = RestClient.builder().baseUrl("http://bc-data/api")
        val server = MockRestServiceServer.bindTo(builder).build()
        server
            .expect(requestTo("http://bc-data/api/trns/pf-1/asset/a-1/trades"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withNoContent())

        assertThatThrownBy { TrnClient(builder.build(), tokenService).getTrades("pf-1", "a-1") }
            .isInstanceOf(BusinessException::class.java)
    }
}