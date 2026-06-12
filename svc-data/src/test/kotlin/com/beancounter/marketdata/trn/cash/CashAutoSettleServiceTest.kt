package com.beancounter.marketdata.trn.cash

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.cash.CashAutoSettleService
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.utils.BcMvcHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Auto-settle generates a compensating cash transfer (WITHDRAWAL + DEPOSIT)
 * between a trade's portfolio and the linked funding portfolio when a
 * cash-impacting trade is saved.
 *
 * Trigger surface and rules — see
 * `bc-claude/plans/my-workflow-on-trade-adaptive-blanket.md`.
 */
@SpringMvcDbTest
class CashAutoSettleServiceTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @MockitoBean
    private lateinit var fxClientService: FxRateService

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var trnService: TrnService

    @Autowired
    private lateinit var trnRepository: TrnRepository

    @Autowired
    private lateinit var autoSettleService: CashAutoSettleService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var systemUserService: Registration

    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var bcMvcHelper: BcMvcHelper
    private lateinit var testUser: SystemUser
    private lateinit var usdCashAsset: Asset
    private lateinit var master: Portfolio
    private lateinit var invest: Portfolio
    private lateinit var aaplAsset: Asset

    private val tenK = BigDecimal("10000.00")
    private val seedAmount = BigDecimal("50000.00")

    @BeforeEach
    fun configure() {
        testUser = SystemUser()
        val token = mockAuthConfig.login(testUser, systemUserService)
        bcMvcHelper = BcMvcHelper(mockMvc, token)

        Mockito
            .`when`(fxClientService.getRates(any(), any()))
            .thenReturn(FxResponse(FxPairResults()))

        usdCashAsset = getCashBalance(Constants.USD)
        aaplAsset =
            assetService
                .handle(
                    AssetRequest(
                        AssetInput(Constants.NASDAQ.code, "AAPL"),
                        "AAPL"
                    )
                ).data["AAPL"]!!

        val uniqueId = UUID.randomUUID().toString().substring(0, 8)
        master =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "MASTER_$uniqueId",
                    base = "USD",
                    currency = "USD"
                )
            )
        invest =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "INV_$uniqueId",
                    base = "USD",
                    currency = "USD",
                    cashPortfolioId = master.id
                )
            )
        // Seed master with a USD deposit so the balance check passes.
        mockAuthConfig.login(testUser, systemUserService)
        trnService.save(
            master,
            TrnRequest(
                master.id,
                listOf(
                    TrnInput(
                        assetId = usdCashAsset.id,
                        cashAssetId = usdCashAsset.id,
                        trnType = TrnType.DEPOSIT,
                        tradeAmount = seedAmount,
                        tradeCurrency = "USD",
                        cashCurrency = "USD",
                        price = BigDecimal.ONE,
                        tradeDate = LocalDate.now().minusDays(1),
                        status = TrnStatus.SETTLED
                    )
                )
            )
        )
    }

    @Test
    fun `BUY emits W in master and D in invest with stamped callerRef group`() {
        // Save a BUY in the invest portfolio. cashAsset = USD; cashAmount > 0.
        mockAuthConfig.login(testUser, systemUserService)
        val buys =
            trnService.save(
                invest,
                TrnRequest(
                    invest.id,
                    listOf(
                        TrnInput(
                            assetId = aaplAsset.id,
                            cashAssetId = usdCashAsset.id,
                            trnType = TrnType.BUY,
                            quantity = BigDecimal("10"),
                            price = BigDecimal("200"),
                            tradeAmount = BigDecimal("2000"),
                            tradeCurrency = "USD",
                            cashCurrency = "USD",
                            cashAmount = BigDecimal("-2000"),
                            tradeCashRate = BigDecimal.ONE,
                            tradeDate = LocalDate.now(),
                            status = TrnStatus.SETTLED
                        )
                    )
                )
            )

        val buy = buys.single()
        val siblings = findAutoSiblings(buy)

        assertThat(siblings).hasSize(2)
        val withdrawal = siblings.single { it.trnType == TrnType.WITHDRAWAL }
        val deposit = siblings.single { it.trnType == TrnType.DEPOSIT }

        // Direction: BUY → debit cash, so pull from master, push into invest
        assertThat(withdrawal.portfolio.id).isEqualTo(master.id)
        assertThat(deposit.portfolio.id).isEqualTo(invest.id)

        // Both legs same amount, same cash asset, settled
        assertThat(withdrawal.tradeAmount).isEqualByComparingTo("2000")
        assertThat(deposit.tradeAmount).isEqualByComparingTo("2000")
        assertThat(withdrawal.cashAsset?.id).isEqualTo(usdCashAsset.id)
        assertThat(deposit.cashAsset?.id).isEqualTo(usdCashAsset.id)
        assertThat(withdrawal.status).isEqualTo(TrnStatus.SETTLED)
        assertThat(deposit.status).isEqualTo(TrnStatus.SETTLED)

        // Group key: provider = BC-AUTO, batch = parent.callerId
        assertThat(withdrawal.callerRef?.provider).isEqualTo("BC-AUTO")
        assertThat(deposit.callerRef?.provider).isEqualTo("BC-AUTO")
        assertThat(withdrawal.callerRef?.batch).isEqualTo(buy.callerRef?.callerId)
        assertThat(deposit.callerRef?.batch).isEqualTo(buy.callerRef?.callerId)
    }

    @Test
    fun `SELL emits W in invest and D in master (reverse direction)`() {
        mockAuthConfig.login(testUser, systemUserService)
        val sells =
            trnService.save(
                invest,
                TrnRequest(
                    invest.id,
                    listOf(
                        TrnInput(
                            assetId = aaplAsset.id,
                            cashAssetId = usdCashAsset.id,
                            trnType = TrnType.SELL,
                            quantity = BigDecimal("5"),
                            price = BigDecimal("220"),
                            tradeAmount = BigDecimal("1100"),
                            tradeCurrency = "USD",
                            cashCurrency = "USD",
                            cashAmount = BigDecimal("1100"),
                            tradeCashRate = BigDecimal.ONE,
                            tradeDate = LocalDate.now(),
                            status = TrnStatus.SETTLED
                        )
                    )
                )
            )

        val sell = sells.single()
        val siblings = findAutoSiblings(sell)
        assertThat(siblings).hasSize(2)

        val withdrawal = siblings.single { it.trnType == TrnType.WITHDRAWAL }
        val deposit = siblings.single { it.trnType == TrnType.DEPOSIT }

        // SELL credits cash, so sweep out of invest back to master
        assertThat(withdrawal.portfolio.id).isEqualTo(invest.id)
        assertThat(deposit.portfolio.id).isEqualTo(master.id)
        assertThat(withdrawal.tradeAmount).isEqualByComparingTo("1100")
        assertThat(deposit.tradeAmount).isEqualByComparingTo("1100")
    }

    @Test
    fun `DEPOSIT never triggers auto-settle (loop guard)`() {
        mockAuthConfig.login(testUser, systemUserService)
        val deposits =
            trnService.save(
                invest,
                TrnRequest(
                    invest.id,
                    listOf(
                        TrnInput(
                            assetId = usdCashAsset.id,
                            cashAssetId = usdCashAsset.id,
                            trnType = TrnType.DEPOSIT,
                            tradeAmount = BigDecimal("500"),
                            tradeCurrency = "USD",
                            cashCurrency = "USD",
                            price = BigDecimal.ONE,
                            tradeDate = LocalDate.now(),
                            status = TrnStatus.SETTLED
                        )
                    )
                )
            )
        val deposit = deposits.single()
        assertThat(findAutoSiblings(deposit)).isEmpty()
    }

    @Test
    fun `BUY in self-funded portfolio (master == trade portfolio) skips auto-settle`() {
        mockAuthConfig.login(testUser, systemUserService)
        // master has no cashPortfolioId set
        val buys =
            trnService.save(
                master,
                TrnRequest(
                    master.id,
                    listOf(
                        TrnInput(
                            assetId = aaplAsset.id,
                            cashAssetId = usdCashAsset.id,
                            trnType = TrnType.BUY,
                            quantity = BigDecimal("1"),
                            price = BigDecimal("100"),
                            tradeAmount = BigDecimal("100"),
                            tradeCurrency = "USD",
                            cashCurrency = "USD",
                            cashAmount = BigDecimal("-100"),
                            tradeCashRate = BigDecimal.ONE,
                            tradeDate = LocalDate.now(),
                            status = TrnStatus.SETTLED
                        )
                    )
                )
            )
        assertThat(findAutoSiblings(buys.single())).isEmpty()
    }

    @Test
    fun `BUY skipped with warning when master has no cash asset history`() {
        // Make a brand-new master with no DEPOSIT seed; link a new invest to it
        val uniqueId = UUID.randomUUID().toString().substring(0, 8)
        val emptyMaster =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "EMPTY_$uniqueId",
                    base = "USD",
                    currency = "USD"
                )
            )
        val newInvest =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "NEW_INV_$uniqueId",
                    base = "USD",
                    currency = "USD",
                    cashPortfolioId = emptyMaster.id
                )
            )

        mockAuthConfig.login(testUser, systemUserService)
        val result =
            autoSettleService.emitCompensatingTransfer(
                buildBuy(newInvest, BigDecimal("-500"))
            )

        assertThat(result.transactions).isEmpty()
        assertThat(result.warnings).isNotEmpty
        assertThat(result.warnings.first().lowercase()).contains("usd").contains("no")
    }

    @Test
    fun `cash history check accepts DIVI as proof master holds the currency`() {
        // Distinct master+invest pair so we control the cash-history seeding.
        val uniqueId = UUID.randomUUID().toString().substring(0, 8)
        val divMaster =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "DIV_MASTER_$uniqueId",
                    base = "USD",
                    currency = "USD"
                )
            )
        val divInvest =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "DIV_INV_$uniqueId",
                    base = "USD",
                    currency = "USD",
                    cashPortfolioId = divMaster.id
                )
            )

        // Seed master with a DIVI (asset = security, cashAsset = USD).
        // The old query (asset.id == cashAssetId) would miss this — the fix
        // queries by cashAsset so DIVI-only history counts.
        mockAuthConfig.login(testUser, systemUserService)
        trnService.save(
            divMaster,
            TrnRequest(
                divMaster.id,
                listOf(
                    TrnInput(
                        assetId = aaplAsset.id,
                        cashAssetId = usdCashAsset.id,
                        trnType = TrnType.DIVI,
                        tradeAmount = BigDecimal("100"),
                        tradeCurrency = "USD",
                        cashCurrency = "USD",
                        cashAmount = BigDecimal("100"),
                        tradeCashRate = BigDecimal.ONE,
                        tradeDate = LocalDate.now().minusDays(2),
                        status = TrnStatus.SETTLED
                    )
                )
            )
        )

        val buy = buildBuy(divInvest, BigDecimal("-200"))
        val siblings = findAutoSiblings(buy)
        assertThat(siblings).hasSize(2)
    }

    @Test
    fun `unsettle deletes the auto-emitted cash pair and reports the removed ids`() {
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-2000"))
        assertThat(findAutoSiblings(buy)).hasSize(2)

        val response = trnService.unsettle(buy.id)

        assertThat(response.updated.id).isEqualTo(buy.id)
        assertThat(response.updated.status).isEqualTo(TrnStatus.PROPOSED)
        // The two cash legs are removed server-side; the ids are reported back.
        assertThat(response.siblings).hasSize(2)
        assertThat(findAutoSiblings(buy)).isEmpty()
    }

    @Test
    fun `a PROPOSED trade emits no cash transfer until it is settled`() {
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-1200"), status = TrnStatus.PROPOSED)

        assertThat(buy.status).isEqualTo(TrnStatus.PROPOSED)
        assertThat(findAutoSiblings(buy)).isEmpty()
    }

    @Test
    fun `settling a PROPOSED trade emits the compensating cash transfer`() {
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-1200"), status = TrnStatus.PROPOSED)
        assertThat(findAutoSiblings(buy)).isEmpty()

        trnService.settleTransactions(invest.id, listOf(buy.id))

        assertThat(findAutoSiblings(buy)).hasSize(2)
    }

    @Test
    fun `unsettle on already PROPOSED trn throws IllegalArgumentException`() {
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-1000"))
        trnService.unsettle(buy.id)
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            trnService.unsettle(buy.id)
        }
    }

    /**
     * Regression for DATA-4Z: stream-consumer threads (trnEventConsumer,
     * csvImportConsumer) have no JWT in scope. When auto-settle resolved
     * the funding portfolio via [PortfolioService.find], `canView` invoked
     * `systemUserService.getOrThrow()` which threw inside the outer
     * `TrnService.save` `@Transactional`. The exception was caught by a
     * `runCatching` block, but the transaction stayed marked rollback-only,
     * so the outer commit blew up with `UnexpectedRollbackException` and
     * the message rebounded forever via the AMQP retry loop.
     */
    @Test
    fun `auto-settle resolves master without JWT (stream consumer path)`() {
        // Save BUY in invest portfolio after CLEARING the security context —
        // simulates the stream-consumer thread that has no JWT.
        SecurityContextHolder.clearContext()
        val buy =
            trnService
                .save(
                    invest,
                    TrnRequest(
                        invest.id,
                        listOf(
                            TrnInput(
                                assetId = aaplAsset.id,
                                cashAssetId = usdCashAsset.id,
                                trnType = TrnType.BUY,
                                quantity = BigDecimal("1"),
                                price = BigDecimal("750"),
                                tradeAmount = BigDecimal("750"),
                                tradeCurrency = "USD",
                                cashCurrency = "USD",
                                cashAmount = BigDecimal("-750"),
                                tradeCashRate = BigDecimal.ONE,
                                tradeDate = LocalDate.now(),
                                status = TrnStatus.SETTLED
                            )
                        )
                    )
                ).single()

        val siblings = findAutoSiblings(buy)
        assertThat(siblings).hasSize(2)
    }

    @Test
    fun `delete returns sibling ids without auto-cascading`() {
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-1500"))
        val siblingIdsBefore = findAutoSiblings(buy).map { it.id }
        assertThat(siblingIdsBefore).hasSize(2)

        val deleteResponse = trnService.deleteWithSiblings(buy.id)

        assertThat(deleteResponse.data).containsExactly(buy.id)
        assertThat(deleteResponse.siblings).containsExactlyInAnyOrderElementsOf(siblingIdsBefore)

        // Siblings still exist — server did NOT cascade. UI will issue follow-up deletes.
        assertThat(trnRepository.findById(siblingIdsBefore[0]).isPresent).isTrue()
        assertThat(trnRepository.findById(siblingIdsBefore[1]).isPresent).isTrue()
    }

    private fun buildBuy(
        portfolio: Portfolio,
        cashAmount: BigDecimal,
        status: TrnStatus = TrnStatus.SETTLED
    ): Trn =
        trnService
            .save(
                portfolio,
                TrnRequest(
                    portfolio.id,
                    listOf(
                        TrnInput(
                            assetId = aaplAsset.id,
                            cashAssetId = usdCashAsset.id,
                            trnType = TrnType.BUY,
                            quantity = BigDecimal("1"),
                            price = cashAmount.abs(),
                            tradeAmount = cashAmount.abs(),
                            tradeCurrency = "USD",
                            cashCurrency = "USD",
                            cashAmount = cashAmount,
                            tradeCashRate = BigDecimal.ONE,
                            tradeDate = LocalDate.now(),
                            status = status
                        )
                    )
                )
            ).single()

    private fun findAutoSiblings(parent: Trn): List<Trn> =
        trnRepository
            .findAll()
            .filter {
                it.callerRef?.provider == "BC-AUTO" &&
                    it.callerRef?.batch == parent.callerRef?.callerId
            }

    private fun getCashBalance(currency: Currency): Asset {
        val cashInput = AssetUtils.getCash(currency.code)
        return assetService
            .handle(
                AssetRequest(
                    mapOf(Pair(currency.code, cashInput))
                )
            ).data[currency.code]!!
    }
}