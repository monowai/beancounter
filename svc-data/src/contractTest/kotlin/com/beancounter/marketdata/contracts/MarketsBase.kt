package com.beancounter.marketdata.contracts

import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeEach
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * Base class for Market Contract tests
 */
class MarketsBase : ContractVerifierBase() {

    @BeforeEach
    fun mock() {
        val mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)
    }
}
