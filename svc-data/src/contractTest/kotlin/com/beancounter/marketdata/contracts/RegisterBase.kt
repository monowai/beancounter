package com.beancounter.marketdata.contracts

import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.currency.CurrencyService
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

/**
 * Base class for Authenticated registration contract tests. This is called by the spring cloud contract verifier
 */
class RegisterBase : ContractVerifierBase() {
    private val notAuthenticated = "not@authenticated.com"

    var notFoundUser = SystemUser(notAuthenticated, notAuthenticated)

    @Autowired
    private lateinit var currencyService: CurrencyService

    @BeforeEach
    fun mock() {
        val mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)

        Mockito.`when`(jwtDecoder.decode(notFoundUser.email))
            .thenReturn(null)

        Mockito.`when`(systemUserRepository.findById(notAuthenticated))
            .thenReturn(Optional.ofNullable(null))

        defaultUser()
    }
}
