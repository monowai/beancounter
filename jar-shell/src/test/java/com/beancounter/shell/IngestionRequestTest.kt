package com.beancounter.shell

import com.beancounter.common.utils.BcJson
import com.beancounter.shell.ingest.IngestionRequest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class IngestionRequestTest {
    private val bcJson = BcJson()

    @Test
    @Throws(Exception::class)
    fun is_SerializationWorking() {
        val ingestionRequest = IngestionRequest(
            "",
            file = "TWEE",
            provider = "TheProvider",
            portfolioCode = "Test",
        )
        Assertions.assertThat(ingestionRequest.ratesIgnored).isTrue
        val json = bcJson.objectMapper.writeValueAsString(ingestionRequest)
        Assertions.assertThat(bcJson.objectMapper.readValue(json, IngestionRequest::class.java))
            .usingRecursiveComparison().isEqualTo(ingestionRequest)
    }
}
