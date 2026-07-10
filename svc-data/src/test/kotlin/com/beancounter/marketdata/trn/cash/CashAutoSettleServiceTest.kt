@file:Suppress("LongMethod") // descriptive test names + linear scenario setup push these past detekt's 60-line cap

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
import com.beancounter.marketdata.cash.AutoSettleBackfill
import com.beancounter.marketdata.cash.CashAutoSettleService
import com.beancounter.marketdata.fx.FxRateService
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.trn.TrnSettlementService
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
    private lateinit var trnSettlementService: TrnSettlementService

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
    fun `BUY with no master cash history still posts the transfer and warns of overdraw`() {
        // Make a brand-new master with no DEPOSIT seed; link a new invest to it.
        // The user opted into central funding, so we post the transfer anyway
        // (master goes negative) rather than silently leaving invest unbalanced.
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
            trnService.saveWithResult(
                newInvest,
                TrnRequest(
                    newInvest.id,
                    listOf(
                        TrnInput(
                            assetId = aaplAsset.id,
                            cashAssetId = usdCashAsset.id,
                            trnType = TrnType.BUY,
                            quantity = BigDecimal("1"),
                            price = BigDecimal("500"),
                            tradeAmount = BigDecimal("500"),
                            tradeCurrency = "USD",
                            cashCurrency = "USD",
                            cashAmount = BigDecimal("-500"),
                            tradeCashRate = BigDecimal.ONE,
                            tradeDate = LocalDate.now(),
                            status = TrnStatus.SETTLED
                        )
                    )
                )
            )

        assertThat(result.warnings).isNotEmpty
        assertThat(result.warnings.first().lowercase()).contains("overdraw")

        val parent = result.trns.single { it.trnType == TrnType.BUY }
        val siblings = findAutoSiblings(parent)
        assertThat(siblings).hasSize(2)
        assertThat(siblings.single { it.trnType == TrnType.WITHDRAWAL }.portfolio.id)
            .isEqualTo(emptyMaster.id)
    }

    @Test
    fun `re-emitting for an already-settled trade reconciles to a single pair`() {
        // An edit that re-saves an already-SETTLED trade must not accumulate
        // pairs — the prior pair is replaced, not added to.
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-2000"))
        val firstPair = findAutoSiblings(buy)
        assertThat(firstPair).hasSize(2)

        val result = autoSettleService.emitCompensatingTransfer(buy)

        // Fresh pair posted, old pair deleted — exactly two legs remain.
        assertThat(result.transactions).hasSize(2)
        assertThat(findAutoSiblings(buy)).hasSize(2)
        assertThat(result.transactions.map { it.id })
            .doesNotContainAnyElementsOf(firstPair.map { it.id })
    }

    @Test
    fun `editing a settled trade re-syncs the compensating pair to the new amount`() {
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-2000"))
        assertThat(findAutoSiblings(buy))
            .allSatisfy { assertThat(it.tradeAmount).isEqualByComparingTo("2000") }

        // Patch the settled trade to a larger amount (the in-place edit path).
        trnService.patch(
            invest,
            buy.id,
            TrnInput(
                assetId = aaplAsset.id,
                cashAssetId = usdCashAsset.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal("1"),
                price = BigDecimal("3000"),
                tradeAmount = BigDecimal("3000"),
                tradeCurrency = "USD",
                cashCurrency = "USD",
                cashAmount = BigDecimal("-3000"),
                tradeCashRate = BigDecimal.ONE,
                tradeDate = LocalDate.now(),
                status = TrnStatus.SETTLED
            )
        )

        val pair = findAutoSiblings(buy)
        assertThat(pair).hasSize(2)
        assertThat(pair).allSatisfy { assertThat(it.tradeAmount).isEqualByComparingTo("3000") }
    }

    @Test
    fun `patching a SETTLED trade to PROPOSED reverts its cash pair to PROPOSED`() {
        // KARS regression: an unsettled equity trade must not leave a settled
        // cash impact. The patch/edit path used to reconcile legs only for SETTLED
        // trades, orphaning SETTLED legs when the parent was edited to PROPOSED.
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-2000"))
        assertThat(findAutoSiblings(buy).map { it.status }).containsOnly(TrnStatus.SETTLED)

        trnService.patch(
            invest,
            buy.id,
            TrnInput(
                assetId = aaplAsset.id,
                cashAssetId = usdCashAsset.id,
                trnType = TrnType.BUY,
                quantity = BigDecimal("1"),
                price = BigDecimal("2000"),
                tradeAmount = BigDecimal("2000"),
                tradeCurrency = "USD",
                cashCurrency = "USD",
                cashAmount = BigDecimal("-2000"),
                tradeCashRate = BigDecimal.ONE,
                tradeDate = LocalDate.now(),
                status = TrnStatus.PROPOSED
            )
        )

        val siblings = findAutoSiblings(buy)
        assertThat(siblings).hasSize(2)
        assertThat(siblings.map { it.status }).containsOnly(TrnStatus.PROPOSED)
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
    fun `unsettle reverts the auto-emitted cash pair to PROPOSED and reports the ids`() {
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-2000"))
        assertThat(findAutoSiblings(buy).map { it.status }).containsOnly(TrnStatus.SETTLED)

        val response = trnSettlementService.unsettle(buy.id)

        assertThat(response.updated.id).isEqualTo(buy.id)
        assertThat(response.updated.status).isEqualTo(TrnStatus.PROPOSED)
        // Legs stay linked but move in sync — reverted to PROPOSED, not deleted.
        assertThat(response.siblings).hasSize(2)
        val siblings = findAutoSiblings(buy)
        assertThat(siblings).hasSize(2)
        assertThat(siblings.map { it.status }).containsOnly(TrnStatus.PROPOSED)
    }

    @Test
    fun `a PROPOSED trade emits a PROPOSED cash pair that moves in sync`() {
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-1200"), status = TrnStatus.PROPOSED)

        assertThat(buy.status).isEqualTo(TrnStatus.PROPOSED)
        // Legs exist but mirror the parent: PROPOSED, so no settled cash impact.
        val siblings = findAutoSiblings(buy)
        assertThat(siblings).hasSize(2)
        assertThat(siblings.map { it.status }).containsOnly(TrnStatus.PROPOSED)
    }

    @Test
    fun `settling a PROPOSED trade transitions its cash pair to SETTLED`() {
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-1200"), status = TrnStatus.PROPOSED)
        assertThat(findAutoSiblings(buy).map { it.status }).containsOnly(TrnStatus.PROPOSED)

        trnSettlementService.settleTransactions(invest.id, listOf(buy.id))

        val siblings = findAutoSiblings(buy)
        assertThat(siblings).hasSize(2)
        assertThat(siblings.map { it.status }).containsOnly(TrnStatus.SETTLED)
    }

    @Test
    fun `bulk unsettle reverts each trade and its cash legs to PROPOSED`() {
        mockAuthConfig.login(testUser, systemUserService)
        val b1 = buildBuy(invest, BigDecimal("-1000"))
        val b2 = buildBuy(invest, BigDecimal("-1500"))
        assertThat(findAutoSiblings(b1)).hasSize(2)
        assertThat(findAutoSiblings(b2)).hasSize(2)

        val result = trnSettlementService.unsettleTransactions(invest.id, listOf(b1.id, b2.id))

        assertThat(result).hasSize(2)
        assertThat(result.map { it.status }).containsOnly(TrnStatus.PROPOSED)
        assertThat(findAutoSiblings(b1).map { it.status }).containsOnly(TrnStatus.PROPOSED)
        assertThat(findAutoSiblings(b2).map { it.status }).containsOnly(TrnStatus.PROPOSED)
    }

    @Test
    fun `unsettle on already PROPOSED trn throws IllegalArgumentException`() {
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-1000"))
        trnSettlementService.unsettle(buy.id)
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            trnSettlementService.unsettle(buy.id)
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

    @Test
    fun `BUY in DBS settling IB-USD transfers from SGD funding portfolio and leaves DBS cash flat`() {
        // Broker settles USD into a named cash asset "IB-USD". The SGD funding
        // portfolio holds that IB-USD balance; DBS points its cashPortfolioId at it.
        val uniqueId = UUID.randomUUID().toString().substring(0, 8)
        val ibUsd = namedCashAsset("IB-USD", Constants.USD)
        val msft =
            assetService
                .handle(
                    AssetRequest(
                        AssetInput(Constants.NASDAQ.code, "MSFT"),
                        "MSFT"
                    )
                ).data["MSFT"]!!

        val sgdFunding =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "SGD_$uniqueId",
                    base = "SGD",
                    currency = "SGD"
                )
            )
        val dbs =
            bcMvcHelper.portfolio(
                PortfolioInput(
                    code = "DBS_$uniqueId",
                    base = "SGD",
                    currency = "SGD",
                    cashPortfolioId = sgdFunding.id
                )
            )

        // Seed the funding portfolio with an IB-USD balance so the cash-history
        // gate passes (SGD:IB-USD funded by the brokerage settlement).
        mockAuthConfig.login(testUser, systemUserService)
        trnService.save(
            sgdFunding,
            TrnRequest(
                sgdFunding.id,
                listOf(
                    TrnInput(
                        assetId = ibUsd.id,
                        cashAssetId = ibUsd.id,
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

        // Buy MSFT in DBS, settling against IB-USD.
        val buy =
            trnService
                .save(
                    dbs,
                    TrnRequest(
                        dbs.id,
                        listOf(
                            TrnInput(
                                assetId = msft.id,
                                cashAssetId = ibUsd.id,
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
                ).single()

        val siblings = findAutoSiblings(buy)
        assertThat(siblings).hasSize(2)
        val withdrawal = siblings.single { it.trnType == TrnType.WITHDRAWAL }
        val deposit = siblings.single { it.trnType == TrnType.DEPOSIT }

        // Transfer is SGD:IB-USD -> DBS:IB-USD
        assertThat(withdrawal.portfolio.id).isEqualTo(sgdFunding.id)
        assertThat(deposit.portfolio.id).isEqualTo(dbs.id)
        assertThat(withdrawal.cashAsset?.id).isEqualTo(ibUsd.id)
        assertThat(deposit.cashAsset?.id).isEqualTo(ibUsd.id)
        assertThat(withdrawal.tradeAmount).isEqualByComparingTo("2000")
        assertThat(deposit.tradeAmount).isEqualByComparingTo("2000")
        // Cash legs carry a signed cashAmount (rate defaults to 1).
        assertThat(withdrawal.cashAmount).isEqualByComparingTo("-2000")
        assertThat(deposit.cashAmount).isEqualByComparingTo("2000")

        // DBS:IB-USD nets to zero — the BUY debit (-2000) is matched by the
        // auto-settle DEPOSIT (+2000). Mirror the position engine's cash rule:
        // pure cash trns (DEPOSIT/WITHDRAWAL) move the balance by `quantity`,
        // security trades by their `cashAmount` (DepositBehaviour.accumulate).
        val dbsIbUsdBalance =
            trnRepository
                .findAll()
                .filter {
                    it.portfolio.id == dbs.id &&
                        (it.asset.id == ibUsd.id || it.cashAsset?.id == ibUsd.id)
                }.sumOf {
                    if (TrnType.isCash(it.trnType)) it.quantity else it.cashAmount
                }
        assertThat(dbsIbUsdBalance).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `backfill emits PROPOSED legs for legless proposed trades and is idempotent`() {
        mockAuthConfig.login(testUser, systemUserService)
        val buy = buildBuy(invest, BigDecimal("-1000"), status = TrnStatus.PROPOSED)
        // Simulate a pre-mirror trade: strip its auto legs so it has none, the
        // state of trades created/unsettled before legs mirrored parent status.
        trnRepository.deleteAll(findAutoSiblings(buy))
        assertThat(findAutoSiblings(buy)).isEmpty()

        val backfill = AutoSettleBackfill(trnRepository, autoSettleService)
        backfill.run()

        val legs = findAutoSiblings(buy)
        assertThat(legs).hasSize(2)
        assertThat(legs.map { it.status }).containsOnly(TrnStatus.PROPOSED)

        // Idempotent: the trade now has legs, so a second run emits nothing new.
        backfill.run()
        assertThat(findAutoSiblings(buy)).hasSize(2)
    }

    @Test
    fun `backfill skips a proposed trade with zero cash amount (no phantom legs)`() {
        mockAuthConfig.login(testUser, systemUserService)
        // A proposed DIVI whose cash amount is not yet known — no transfer to post.
        val divi =
            trnService
                .save(
                    invest,
                    TrnRequest(
                        invest.id,
                        listOf(
                            TrnInput(
                                assetId = aaplAsset.id,
                                cashAssetId = usdCashAsset.id,
                                trnType = TrnType.DIVI,
                                tradeAmount = BigDecimal.ZERO,
                                cashAmount = BigDecimal.ZERO,
                                tradeCurrency = "USD",
                                cashCurrency = "USD",
                                price = BigDecimal.ONE,
                                tradeDate = LocalDate.now(),
                                status = TrnStatus.PROPOSED
                            )
                        )
                    )
                ).single()
        assertThat(findAutoSiblings(divi)).isEmpty()

        AutoSettleBackfill(trnRepository, autoSettleService).run()

        assertThat(findAutoSiblings(divi)).isEmpty()
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

    private fun namedCashAsset(
        code: String,
        currency: Currency
    ): Asset =
        assetService
            .handle(
                AssetRequest(
                    mapOf(
                        code to
                            AssetInput(
                                market = "CASH",
                                code = code,
                                name = "$code Balance",
                                currency = currency.code,
                                category = "cash"
                            )
                    )
                )
            ).data[code]!!

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