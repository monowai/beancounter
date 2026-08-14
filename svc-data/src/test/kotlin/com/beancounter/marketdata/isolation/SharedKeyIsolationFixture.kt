package com.beancounter.marketdata.isolation

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.utils.BcMvcHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Shared body for the pair of tests that guard per-class database isolation (#1081).
 *
 * Both subclasses write a trn under the *same* globally-unique key. `Trn` is unique on
 * `(provider, batch, callerId)` — not scoped by owner or portfolio — and `CallerRef.from`
 * fills a blank provider with `BC` and a blank batch with today's date. So
 * `CallerRef(callerId = "1")` in two unrelated classes is one key.
 *
 * Against a single shared database the second class to run gets a 409 and fails. They
 * pass together only when each class has its own database, which is exactly the property
 * being guarded. Deleting [com.beancounter.marketdata.H2PerClassContextCustomizerFactory]
 * or its `spring.factories` registration turns one of these red.
 *
 * The pair is deliberately order-independent: whichever runs second is the one that would
 * fail, and JUnit does not promise which that is.
 */
abstract class SharedKeyIsolationFixture {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @Autowired
    private lateinit var defaultEnricher: DefaultEnricher

    private lateinit var bcMvcHelper: BcMvcHelper

    @BeforeEach
    fun configure() {
        enrichmentFactory.register(defaultEnricher)
        bcMvcHelper = BcMvcHelper(mockMvc, mockAuthConfig.getUserToken(Constants.systemUser))
        bcMvcHelper.registerUser()
    }

    /**
     * Writes a trn under the contended key. [portfolioCode] differs per subclass only so
     * the failure, when isolation regresses, is a 409 on the trn rather than on the
     * portfolio — the trn is the constraint under test.
     */
    protected fun saveTrnUnderContendedKey(portfolioCode: String) {
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput(portfolioCode, "Isolation guard", currency = USD.code)
            )
        val trnRequest =
            TrnRequest(
                portfolio.id,
                listOf(
                    TrnInput(
                        // Bare callerId, no provider, no batch — the collision-prone
                        // shape this guard exists to keep working.
                        callerRef = CallerRef(callerId = CONTENDED_CALLER_ID),
                        assetId = bcMvcHelper.asset(AssetRequest(Constants.msftInput)).id,
                        trnType = TrnType.BUY,
                        quantity = BigDecimal.ONE,
                        price = BigDecimal("10.00"),
                        tradeDate = LocalDate.of(2026, 8, 1),
                        status = TrnStatus.SETTLED
                    )
                )
            )

        val trnResponse: TrnResponse =
            objectMapper.readValue(
                bcMvcHelper.postTrn(trnRequest).response.contentAsString,
                TrnResponse::class.java
            )

        assertThat(trnResponse.data.trns).hasSize(1)
    }

    private companion object {
        const val CONTENDED_CALLER_ID = "isolation-guard"
    }
}