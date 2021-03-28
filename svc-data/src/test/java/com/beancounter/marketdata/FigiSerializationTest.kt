package com.beancounter.marketdata

import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.assets.figi.FigiResponse
import com.fasterxml.jackson.core.type.TypeReference
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class FigiSerializationTest {
    @Test
    @Throws(Exception::class)
    fun is_ResultSerializable() {
        val responses: Collection<FigiResponse> = BcJson().objectMapper.readValue(
            ClassPathResource("/contracts" + "/figi/multi-asset-response.json").file,
            object : TypeReference<Collection<FigiResponse>>() {}
        )
        Assertions.assertThat(responses).hasSize(2)
    }
}
