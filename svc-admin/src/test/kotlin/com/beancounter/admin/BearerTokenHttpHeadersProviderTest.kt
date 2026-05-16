package com.beancounter.admin

import de.codecentric.boot.admin.server.domain.entities.Instance
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class BearerTokenHttpHeadersProviderTest {
    private val instance: Instance = mock()

    @Test
    fun `attaches Authorization Bearer when token configured`() {
        val provider = BearerTokenHttpHeadersProvider("abc.def.ghi")

        val headers = provider.getHeaders(instance)

        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer abc.def.ghi")
    }

    @Test
    fun `omits Authorization header when token blank`() {
        val provider = BearerTokenHttpHeadersProvider("")

        val headers = provider.getHeaders(instance)

        assertThat(headers.getFirst("Authorization")).isNull()
    }
}