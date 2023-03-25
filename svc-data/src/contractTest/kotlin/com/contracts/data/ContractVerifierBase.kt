package com.contracts.data

import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.NoAuthConfig
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.trn.CashServices
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Base for contract testing. Mocks commonly used services.
 */
@SpringBootTest(
    classes = [MarketDataBoot::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
)
@WebAppConfiguration
@ActiveProfiles("contracts")
@AutoConfigureNoAuth
class ContractVerifierBase {

    internal var dateUtils = DateUtils()

    internal val rateDate = "2019-10-18"

    @Autowired
    internal lateinit var context: WebApplicationContext

    @MockBean
    internal lateinit var keyGenUtils: KeyGenUtils

    @MockBean
    internal lateinit var systemUserRepository: SystemUserRepository

    @MockBean
    internal lateinit var cashServices: CashServices

    @Autowired
    lateinit var authConfig: NoAuthConfig

    @BeforeEach
    fun mock() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)
    }
}
