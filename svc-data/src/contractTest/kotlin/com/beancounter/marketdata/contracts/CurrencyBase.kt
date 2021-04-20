package com.beancounter.marketdata.contracts

import com.beancounter.marketdata.currency.CurrencyService
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * Currency Contract Tests.
 */

class CurrencyBase : ContractVerifierBase() {
    @Autowired
    private lateinit var currencyService: CurrencyService

    @BeforeEach
    fun mock() {
        val mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)
    }
}
