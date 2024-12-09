package com.contracts.data

import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.NoWebAuth
import com.beancounter.auth.TokenService
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.cash.CashServices
import io.restassured.RestAssured
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles

/**
 * Base for contract testing. Mocks commonly used services.
 */
@SpringBootTest(
    classes = [MarketDataBoot::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("contracts")
@AutoConfigureNoAuth
@Import(
    ContractHelper::class,
    NoWebAuth::class
)
class ContractVerifierBase {
    @LocalServerPort
    lateinit var port: String

    internal val rateDate = "2019-10-18"

    @MockBean
    internal lateinit var keyGenUtils: KeyGenUtils

    @MockBean
    internal lateinit var jwtDecoder: JwtDecoder

    @MockBean
    internal lateinit var tokenService: TokenService

    @MockBean
    internal lateinit var systemUserService: SystemUserService

    @Autowired
    lateinit var noWebAuth: NoWebAuth

    @MockBean
    internal lateinit var mockCashServices: CashServices

    @BeforeEach
    fun mock() {
        RestAssured.port = Integer.valueOf(port)
        Mockito.`when`(tokenService.bearerToken).thenReturn("")
    }
}