package com.beancounter.shell

import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.shell.ingest.IngestionRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * IntegrationRequest Pojo tests
 */
class IngestionRequestTest {
    @Test
    fun is_SerializationWorking() {
        val ingestionRequest =
            IngestionRequest(
                "",
                file = "TWEE",
                provider = "TheProvider",
                portfolioCode = "Test"
            )
        assertThat(ingestionRequest.ratesIgnored).isTrue
        val json = objectMapper.writeValueAsString(ingestionRequest)
        assertThat(
            objectMapper.readValue(
                json,
                IngestionRequest::class.java
            )
        ).usingRecursiveComparison()
            .isEqualTo(ingestionRequest)
    }
}