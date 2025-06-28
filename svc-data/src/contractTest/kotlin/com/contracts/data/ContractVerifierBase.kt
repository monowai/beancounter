package com.contracts.data

import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.TokenService
import com.beancounter.auth.server.NoAuthSecurityConfig
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.CashTrnServices
import io.restassured.RestAssured
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

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
    NoAuthSecurityConfig::class
)
class ContractVerifierBase {
    @LocalServerPort
    lateinit var port: String

    internal val rateDate = "2019-10-18"

    @MockitoBean
    internal lateinit var keyGenUtils: KeyGenUtils

    @MockitoBean
    internal lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    internal lateinit var tokenService: TokenService

    @MockitoBean
    internal lateinit var systemUserService: SystemUserService

    @MockitoBean
    internal lateinit var mockCashTrnServices: CashTrnServices

    @BeforeEach
    fun mock() {
        RestAssured.port = Integer.valueOf(port)
        Mockito.`when`(tokenService.bearerToken).thenReturn("")
    }
}