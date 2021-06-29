package com.beancounter.marketdata

import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.assets.figi.FigiResponse
import com.fasterxml.jackson.core.type.TypeReference
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

/**
 * Mocked Figi responses serialize
 */
class FigiSerializationTest {
    @Test
    @Throws(Exception::class)
    fun figiResponseSerializable() {
        val responses: Collection<FigiResponse> = BcJson().objectMapper.readValue(
            ClassPathResource("/mock/figi/multi-asset-response.json").file,
            object : TypeReference<Collection<FigiResponse>>() {}
        )
        Assertions.assertThat(responses).hasSize(2)
    }
}
