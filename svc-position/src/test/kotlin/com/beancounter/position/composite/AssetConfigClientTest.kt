package com.beancounter.position.composite

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Drives [AssetConfigClient.findAll] against a [MockRestServiceServer] so a
 * 200 that carries no usable payload is a failure, never "no configs".
 */
class AssetConfigClientTest {
    private fun clientWithServer(): Pair<AssetConfigClient, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl(BASE_URL)
        val server = MockRestServiceServer.bindTo(builder).build()
        val tokenService = mock<TokenService>()
        whenever(tokenService.bearerToken).thenReturn("Bearer token")
        return AssetConfigClient(builder.build(), tokenService) to server
    }

    @Test
    fun `should return every asset config in the response`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo("$BASE_URL/assets/config"))
            .andRespond(withSuccess(CONFIGS_JSON, MediaType.APPLICATION_JSON))

        val result = client.findAll()

        assertThat(result).hasSize(1)
        assertThat(result.first().assetId).isEqualTo("cpf")
        assertThat(result.first().subAccounts.map { it.code }).containsExactly("OA")
        server.verify()
    }

    @Test
    fun `should fail when asset config list has no body`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo("$BASE_URL/assets/config"))
            .andRespond(withSuccess())

        assertThatThrownBy { client.findAll() }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("/assets/config")
    }

    @Test
    fun `should fail when asset config list has no data field`() {
        val (client, server) = clientWithServer()
        server
            .expect(method(HttpMethod.GET))
            .andExpect(requestTo("$BASE_URL/assets/config"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        assertThatThrownBy { client.findAll() }
            .isInstanceOf(RestClientException::class.java)
    }

    companion object {
        private const val BASE_URL = "http://svc-data/api"
        private const val CONFIGS_JSON =
            """
            {"data":[{"assetId":"cpf","rentalCurrency":"SGD","subAccounts":[{"code":"OA","balance":100}]}]}
            """
    }
}