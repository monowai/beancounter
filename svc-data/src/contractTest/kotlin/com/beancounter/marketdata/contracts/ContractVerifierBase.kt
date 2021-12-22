package com.beancounter.marketdata.contracts

import com.beancounter.auth.common.TokenService
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.trn.CashServices
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.web.context.WebApplicationContext

/**
 * Base for contract testing. Mocks commonly used services.
 */
@SpringBootTest(
    classes = [MarketDataBoot::class],
    properties = ["auth.enabled=false"],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@WebAppConfiguration
@ActiveProfiles("contracts")
class ContractVerifierBase {

    internal var dateUtils = DateUtils()

    internal val rateDate = "2019-10-18"

    @MockBean
    internal lateinit var keyGenUtils: KeyGenUtils

    @MockBean
    internal lateinit var jwtDecoder: JwtDecoder

    @MockBean
    internal lateinit var systemUserRepository: SystemUserRepository

    @MockBean
    internal lateinit var tokenService: TokenService

    @MockBean
    internal lateinit var cashServices: CashServices

    @Autowired
    internal lateinit var context: WebApplicationContext
}
