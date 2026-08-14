package com.beancounter.marketdata.trn

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.DefaultEnricher
import com.beancounter.marketdata.assets.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.utils.BcMvcHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Reproduces a production 500 caused by [CashTrnServices.getCashAsset] swallowing
 * a [com.beancounter.common.exception.NotFoundException] from [com.beancounter.marketdata.assets.AssetFinder.find].
 *
 * bc-view sends the settlement asset's CODE (e.g. "IBRK-USD", after stripping the
 * owner prefix) rather than its generated id — see
 * `bc-view/src/lib/utils/trns/cashFormHelpers.ts`. `CashTrnServices.getCashAsset`
 * optimistically tries `assetFinder.find(cashAccountCode)` first "for backward
 * compatibility", assuming the caller passed a UUID. `AssetFinder` is
 * `@Service @Transactional`, so that failed lookup runs as a PARTICIPATING
 * transaction nested inside `TrnService.saveWithResult`'s own `@Transactional`.
 * `find()` throws `NotFoundException` (a `RuntimeException`) when the code isn't
 * a real id — Spring's default rollback rule fires on any unchecked exception, so
 * the *inner* proxy marks the shared physical transaction rollback-only before
 * the exception is rethrown to the caller.
 *
 * `getCashAsset`'s `try/catch` swallows that exception and falls through to
 * `assetFinder.findLocally(...)`, which resolves the asset correctly by code.
 * The trn then appears to build and save without error — but the transaction is
 * already poisoned. When the OUTER `@Transactional` on `TrnService` (entered via
 * `TrnController.update` -> `POST /trns`) tries to commit, Spring sees the
 * rollback-only flag and refuses to commit a transaction whose participant asked
 * for a rollback, throwing `UnexpectedRollbackException` instead. The caller sees
 * a 500 even though every individual step, taken on its own, "succeeded".
 *
 * Only a real `@Transactional` proxy chain (this `@SpringMvcDbTest`, no
 * `@Transactional` on the test itself) can reproduce this — a Mockito-only unit
 * test never enters a Spring transaction, so it can't observe rollback-only
 * poisoning or the commit-time `UnexpectedRollbackException`.
 */
@SpringMvcDbTest
class TrnCashAssetByCodeTest {
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

    @Test
    fun `should save a trn when cashAssetId is an asset code rather than an id`() {
        val portfolio =
            bcMvcHelper.portfolio(
                PortfolioInput("CASH-CODE", "Cash code rollback", currency = USD.code)
            )

        // Interactive HTTP path — owner left blank so PrivateMarketEnricher
        // resolves the authenticated caller and stamps the owner prefix onto
        // the persisted code, e.g. "<ownerId>.IBRK-USD".
        val cashAssetInput = AssetInput.toAccount(USD, "IBRK-USD", "IBKR USD Account", owner = "")
        val cashAsset = bcMvcHelper.asset(AssetRequest(cashAssetInput))
        assertThat(cashAsset.code).isEqualTo("${portfolio.owner.id}.IBRK-USD")

        // bc-view's cashFormHelpers.ts sends the settlement account's CODE
        // (owner prefix stripped), not its generated id.
        val incomeTrn =
            TrnInput(
                callerRef = CallerRef(callerId = "1"),
                assetId = cashAsset.id,
                cashAssetId = "IBRK-USD",
                cashCurrency = USD.code,
                trnType = TrnType.INCOME,
                tradeAmount = BigDecimal("25.00"),
                price = BigDecimal.ONE,
                tradeDate = LocalDate.of(2026, 8, 1),
                status = TrnStatus.SETTLED
            )
        val trnRequest = TrnRequest(portfolio.id, listOf(incomeTrn))

        val trnResponse: TrnResponse =
            objectMapper.readValue(
                bcMvcHelper.postTrn(trnRequest).response.contentAsString,
                TrnResponse::class.java
            )

        assertThat(trnResponse.data.trns).hasSize(1)
        val savedTrn = trnResponse.data.trns.first()
        assertThat(trnResponse.data.cashAsset(savedTrn))
            .isNotNull
            .isEqualTo(cashAsset)
    }
}