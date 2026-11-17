package com.beancounter.marketdata.broker.reconcile

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.Registration
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Broker
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.broker.BrokerRepository
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.trn.broker.TrnBrokerService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Recording the rows a user confirmed from a brokerage statement: what is stored, what
 * the broker then holds, and that a refused submission stores nothing.
 */
@SpringMvcDbTest
class ReconcileCommitTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var reconcileCommitService: ReconcileCommitService

    @Autowired
    private lateinit var trnBrokerService: TrnBrokerService

    @Autowired
    private lateinit var brokerRepository: BrokerRepository

    @Autowired
    private lateinit var portfolioService: PortfolioService

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var assetFinder: AssetFinder

    @Autowired
    private lateinit var trnRepository: TrnRepository

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var systemUserService: Registration

    private fun login(name: String): SystemUser {
        val email = "rcc-$name@test.com"
        val user = SystemUser(id = email, email = email)
        mockAuthConfig.login(user, systemUserService)
        return user
    }

    private fun portfolio(code: String): Portfolio =
        portfolioService
            .save(listOf(PortfolioInput(code = code, name = code, currency = USD.code)))
            .first()

    private fun asset(code: String): Asset =
        assetService
            .handle(AssetRequest(AssetInput(NASDAQ.code, code), code))
            .data
            .getValue(code)

    private fun broker(
        name: String,
        owner: SystemUser
    ): Broker = brokerRepository.save(Broker(name = name, owner = owner))

    private fun bought(
        portfolio: Portfolio,
        broker: Broker,
        asset: Asset,
        quantity: String
    ) {
        trnRepository.save(
            Trn(
                trnType = TrnType.BUY,
                asset = asset,
                quantity = BigDecimal(quantity),
                portfolio = portfolio,
                broker = broker,
                tradeDate = LocalDate.of(2026, 1, 1)
            )
        )
    }

    private fun add(
        asset: Asset,
        quantity: String,
        cost: String? = "100"
    ) = ConfirmedRow(asset.id, TrnType.ADD, BigDecimal(quantity), cost?.let { BigDecimal(it) })

    private fun reduce(
        asset: Asset,
        quantity: String
    ) = ConfirmedRow(asset.id, TrnType.REDUCE, BigDecimal(quantity))

    private fun confirmed(
        portfolio: Portfolio,
        broker: Broker,
        uploadId: String,
        vararg rows: ConfirmedRow
    ) = ConfirmedHoldings(portfolio.id, broker.id, uploadId, rows = rows.toList())

    private fun trnsOf(portfolio: Portfolio): List<Trn> =
        trnRepository.findAll().filter {
            it.portfolio.id ==
                portfolio.id
        }

    private fun trnsOwnedBy(user: SystemUser): List<Trn> =
        trnRepository.findAll().filter { it.portfolio.owner.id == user.id }

    /** What [portfolio] holds of [asset] at [broker], as the broker holdings view reports it. */
    private fun heldAt(
        broker: Broker,
        portfolio: Portfolio,
        asset: Asset
    ): BigDecimal =
        trnBrokerService
            .getBrokerHoldings(broker.id)
            .holdings
            .filter { it.assetId == asset.id }
            .flatMap { it.portfolioGroups }
            .filter { it.portfolioId == portfolio.id }
            .fold(BigDecimal.ZERO) { total, group -> total.add(group.quantity) }

    private fun assertRefused(
        submission: ConfirmedHoldings,
        messagePart: String
    ) {
        assertThatThrownBy { reconcileCommitService.commit(submission) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessageContaining(messagePart)
    }

    @Test
    fun `should record an add as a settled transaction at the broker`() {
        val user = login("add")
        val portfolio = portfolio("RCC-ADD")
        val asset = asset("RCCA1")
        val broker = broker("RCC-ADD-B", user)
        val tradeDate = LocalDate.of(2026, 3, 2)

        val result =
            reconcileCommitService.commit(
                confirmed(portfolio, broker, "rcc-add-upload", add(asset, "8", "100")).copy(tradeDate = tradeDate)
            )

        val stored = trnsOf(portfolio).single()
        assertThat(result.trns.map { it.id }).containsExactly(stored.id)
        assertThat(stored.trnType).isEqualTo(TrnType.ADD)
        assertThat(stored.status).isEqualTo(TrnStatus.SETTLED)
        assertThat(stored.asset.id).isEqualTo(asset.id)
        assertThat(stored.broker?.id).isEqualTo(broker.id)
        assertThat(stored.quantity).isEqualByComparingTo("8")
        assertThat(stored.tradeAmount).isEqualByComparingTo("100")
        assertThat(stored.price).isEqualByComparingTo("12.5")
        assertThat(stored.tradeCurrency.code).isEqualTo(USD.code)
        assertThat(stored.tradeDate).isEqualTo(tradeDate)
        assertThat(stored.callerRef).isEqualTo(CallerRef("SCREENSHOT", "rcc-add-upload", "${user.id}:${asset.id}"))
        assertThat(heldAt(broker, portfolio, asset)).isEqualByComparingTo("8")
    }

    @Test
    fun `should round the unit price of an add to six places`() {
        val user = login("price")
        val portfolio = portfolio("RCC-PRICE")
        val asset = asset("RCCP1")
        val broker = broker("RCC-PRICE-B", user)

        reconcileCommitService.commit(confirmed(portfolio, broker, "rcc-price-upload", add(asset, "3", "100")))

        val stored = trnsOf(portfolio).single()
        assertThat(stored.price).isEqualByComparingTo("33.333333")
        assertThat(stored.tradeAmount).isEqualByComparingTo("100")
    }

    @Test
    fun `should record an add that cost nothing`() {
        val user = login("free")
        val portfolio = portfolio("RCC-FREE")
        val asset = asset("RCCF1")
        val broker = broker("RCC-FREE-B", user)

        reconcileCommitService.commit(confirmed(portfolio, broker, "rcc-free-upload", add(asset, "4", "0")))

        val stored = trnsOf(portfolio).single()
        assertThat(stored.tradeAmount).isEqualByComparingTo("0")
        assertThat(stored.price).isEqualByComparingTo("0")
        assertThat(heldAt(broker, portfolio, asset)).isEqualByComparingTo("4")
    }

    @Test
    fun `should date the transaction today when no trade date is given`() {
        val user = login("today")
        val portfolio = portfolio("RCC-TODAY")
        val asset = asset("RCCT1")
        val broker = broker("RCC-TODAY-B", user)

        reconcileCommitService.commit(confirmed(portfolio, broker, "rcc-today-upload", add(asset, "1")))

        assertThat(trnsOf(portfolio).single().tradeDate).isEqualTo(DateUtils().date)
    }

    @Test
    fun `should lower the holding at the broker with a reduce that carries no value`() {
        val user = login("reduce")
        val portfolio = portfolio("RCC-REDUCE")
        val asset = asset("RCCR1")
        val broker = broker("RCC-REDUCE-B", user)
        bought(portfolio, broker, asset, "10")

        reconcileCommitService.commit(confirmed(portfolio, broker, "rcc-reduce-upload", reduce(asset, "4")))

        val stored = trnsOf(portfolio).single { it.trnType == TrnType.REDUCE }
        assertThat(stored.status).isEqualTo(TrnStatus.SETTLED)
        assertThat(stored.broker?.id).isEqualTo(broker.id)
        assertThat(stored.quantity).isEqualByComparingTo("4")
        assertThat(stored.tradeAmount).isEqualByComparingTo("0")
        assertThat(stored.price).isEqualByComparingTo("0")
        assertThat(heldAt(broker, portfolio, asset)).isEqualByComparingTo("6")
    }

    @Test
    fun `should reduce the whole holding`() {
        val user = login("reduce-all")
        val portfolio = portfolio("RCC-REDALL")
        val asset = asset("RCCR2")
        val broker = broker("RCC-REDALL-B", user)
        bought(portfolio, broker, asset, "10")

        reconcileCommitService.commit(confirmed(portfolio, broker, "rcc-redall-upload", reduce(asset, "10")))

        assertThat(trnsOf(portfolio)).hasSize(2)
        assertThat(heldAt(broker, portfolio, asset)).isEqualByComparingTo("0")
    }

    @Test
    fun `should refuse a reduce above what the portfolio holds at the broker`() {
        val user = login("over")
        val portfolio = portfolio("RCC-OVER")
        val asset = asset("RCCO1")
        val broker = broker("RCC-OVER-B", user)
        val elsewhere = broker("RCC-OVER-ELSE", user)
        bought(portfolio, broker, asset, "10")
        // Held by the same portfolio, but at another broker: not this statement's to reduce.
        bought(portfolio, elsewhere, asset, "50")

        assertRefused(confirmed(portfolio, broker, "rcc-over-upload", reduce(asset, "10.5")), asset.id)

        assertThat(trnsOf(portfolio)).hasSize(2)
        assertThat(heldAt(broker, portfolio, asset)).isEqualByComparingTo("10")
    }

    @Test
    fun `should refuse a reduce of what only another portfolio holds at the broker`() {
        val user = login("other-pf")
        val portfolio = portfolio("RCC-OPF")
        val holder = portfolio("RCC-OPF-H")
        val asset = asset("RCCO2")
        val broker = broker("RCC-OPF-B", user)
        bought(holder, broker, asset, "10")

        assertRefused(confirmed(portfolio, broker, "rcc-opf-upload", reduce(asset, "5")), asset.id)

        assertThat(trnsOf(portfolio)).isEmpty()
        assertThat(heldAt(broker, holder, asset)).isEqualByComparingTo("10")
    }

    @Test
    fun `should record nothing when one row among valid rows is invalid`() {
        val user = login("mixed")
        val portfolio = portfolio("RCC-MIXED")
        val first = asset("RCCM1")
        val second = asset("RCCM2")
        val unheld = asset("RCCM3")
        val broker = broker("RCC-MIXED-B", user)

        assertRefused(
            confirmed(portfolio, broker, "rcc-mixed-upload", add(first, "1"), add(second, "2"), reduce(unheld, "1")),
            unheld.id
        )

        assertThat(trnsOf(portfolio)).isEmpty()
        assertThat(trnRepository.findByCallerRefProviderAndCallerRefBatch("SCREENSHOT", "rcc-mixed-upload")).isEmpty()
    }

    @Test
    fun `should record nothing when a row names an unknown asset`() {
        val user = login("no-asset")
        val portfolio = portfolio("RCC-NOASSET")
        val known = asset("RCCN1")
        val broker = broker("RCC-NOASSET-B", user)
        val unknown = ConfirmedRow("rcc-no-such-asset", TrnType.ADD, BigDecimal.ONE, BigDecimal.TEN)

        assertThatThrownBy {
            reconcileCommitService.commit(confirmed(portfolio, broker, "rcc-noasset-upload", add(known, "1"), unknown))
        }.isInstanceOf(NotFoundException::class.java)
            .hasMessage("Asset not found: rcc-no-such-asset")

        assertThat(trnsOf(portfolio)).isEmpty()
    }

    @Test
    fun `should create an asset a row names by market and code, and record the add`() {
        val user = login("new-asset")
        val portfolio = portfolio("RCC-NEWASSET")
        val broker = broker("RCC-NEWASSET-B", user)
        val row =
            ConfirmedRow(
                assetId = null,
                trnType = TrnType.ADD,
                quantity = BigDecimal("5"),
                cost = BigDecimal("100"),
                market = NASDAQ.code,
                code = "RCCNEW1"
            )

        reconcileCommitService.commit(confirmed(portfolio, broker, "rcc-new-asset-upload", row))

        val created = assetFinder.findLocally(AssetInput(NASDAQ.code, "RCCNEW1"))
        assertThat(created).isNotNull
        val trn = trnsOf(portfolio).single()
        assertThat(trn.asset.id).isEqualTo(created!!.id)
        assertThat(trn.quantity).isEqualByComparingTo("5")
    }

    @Test
    fun `should refuse a row that names neither an asset nor a market and code`() {
        val user = login("no-name")
        val portfolio = portfolio("RCC-NONAME")
        val broker = broker("RCC-NONAME-B", user)
        val row = ConfirmedRow(null, TrnType.ADD, BigDecimal.ONE, BigDecimal.TEN, market = NASDAQ.code)

        assertThatThrownBy {
            reconcileCommitService.commit(confirmed(portfolio, broker, "rcc-noname-upload", row))
        }.isInstanceOf(BusinessException::class.java)
            .hasMessage("A row names its asset by id, or by market and code")

        assertThat(trnsOf(portfolio)).isEmpty()
    }

    @Test
    fun `should create a share class a row writes with a slash under the dot Beancounter uses`() {
        val user = login("slash-class")
        val portfolio = portfolio("RCC-SLASH")
        val broker = broker("RCC-SLASH-B", user)
        val row = ConfirmedRow(null, TrnType.ADD, BigDecimal.ONE, BigDecimal.TEN, market = NASDAQ.code, code = "rccs/b")

        reconcileCommitService.commit(confirmed(portfolio, broker, "rcc-slash-upload", row))

        val created = assetFinder.findLocally(AssetInput(NASDAQ.code, "RCCS.B"))
        assertThat(created).isNotNull
        assertThat(trnsOf(portfolio).single().asset.id).isEqualTo(created!!.id)
    }

    @Test
    fun `should refuse one asset named by id in one row and by market and code in another`() {
        val user = login("twice-mixed")
        val portfolio = portfolio("RCC-TWICEMIX")
        val asset = asset("RCCTM1")
        val broker = broker("RCC-TWICEMIX-B", user)
        val byCode =
            ConfirmedRow(null, TrnType.ADD, BigDecimal.ONE, BigDecimal.TEN, market = NASDAQ.code, code = "RCCTM1")

        assertRefused(
            confirmed(portfolio, broker, "rcc-twice-mixed-upload", add(asset, "1"), byCode),
            "Asset ${asset.id} is named more than once"
        )
        assertThat(trnsOf(portfolio)).isEmpty()
    }

    @Test
    fun `should not find another user's broker, and record nothing`() {
        val other = login("b-other")
        val theirs = broker("RCC-THEIRS", other)
        login("b-caller")
        val portfolio = portfolio("RCC-BCALLER")
        val asset = asset("RCCB1")

        assertThatThrownBy {
            reconcileCommitService.commit(confirmed(portfolio, theirs, "rcc-theirs-upload", add(asset, "1")))
        }.isInstanceOf(NotFoundException::class.java)
            .hasMessage("Broker not found: ${theirs.id}")

        assertThat(trnsOf(portfolio)).isEmpty()
    }

    @Test
    fun `should not find a portfolio the caller may not view`() {
        login("p-owner")
        val hidden = portfolio("RCC-HIDDEN")
        val stranger = login("p-stranger")
        val broker = broker("RCC-STRANGER-B", stranger)
        val asset = asset("RCCH1")

        assertThatThrownBy {
            reconcileCommitService.commit(confirmed(hidden, broker, "rcc-hidden-upload", add(asset, "1")))
        }.isInstanceOf(NotFoundException::class.java)
            .hasMessage("Portfolio not found: ${hidden.id}")

        assertThat(trnsOf(hidden)).isEmpty()
    }

    @Test
    fun `should refuse a row that is not an add or a reduce`() {
        val user = login("type")
        val portfolio = portfolio("RCC-TYPE")
        val asset = asset("RCCY1")
        val broker = broker("RCC-TYPE-B", user)

        for (trnType in listOf(TrnType.BUY, TrnType.SELL, TrnType.DEPOSIT)) {
            val row = ConfirmedRow(asset.id, trnType, BigDecimal.ONE, BigDecimal.TEN)
            assertRefused(confirmed(portfolio, broker, "rcc-type-upload", row), asset.id)
        }

        assertThat(trnsOwnedBy(user)).isEmpty()
    }

    @Test
    fun `should refuse a quantity that is zero or negative`() {
        val user = login("quantity")
        val portfolio = portfolio("RCC-QTY")
        val asset = asset("RCCQ1")
        val broker = broker("RCC-QTY-B", user)
        bought(portfolio, broker, asset, "10")

        for (row in listOf(add(asset, "0"), add(asset, "-1"), reduce(asset, "0"), reduce(asset, "-1"))) {
            assertRefused(confirmed(portfolio, broker, "rcc-qty-upload", row), asset.id)
        }

        assertThat(trnsOf(portfolio)).hasSize(1)
    }

    @Test
    fun `should refuse an add with no cost or a negative cost`() {
        val user = login("cost")
        val portfolio = portfolio("RCC-COST")
        val asset = asset("RCCC1")
        val broker = broker("RCC-COST-B", user)

        for (row in listOf(add(asset, "1", cost = null), add(asset, "1", cost = "-0.01"))) {
            assertRefused(confirmed(portfolio, broker, "rcc-cost-upload", row), asset.id)
        }

        assertThat(trnsOf(portfolio)).isEmpty()
    }

    @Test
    fun `should refuse an asset named twice`() {
        val user = login("twice")
        val portfolio = portfolio("RCC-TWICE")
        val asset = asset("RCCW1")
        val broker = broker("RCC-TWICE-B", user)

        assertRefused(confirmed(portfolio, broker, "rcc-twice-upload", add(asset, "1"), add(asset, "2")), asset.id)

        assertThat(trnsOf(portfolio)).isEmpty()
    }

    @Test
    fun `should refuse a blank upload id`() {
        val user = login("blank")
        val portfolio = portfolio("RCC-BLANK")
        val asset = asset("RCCK1")
        val broker = broker("RCC-BLANK-B", user)

        for (uploadId in listOf("", "   ")) {
            assertRefused(confirmed(portfolio, broker, uploadId, add(asset, "1")), "upload")
        }

        assertThat(trnsOf(portfolio)).isEmpty()
    }

    @Test
    fun `should refuse a submission with no rows`() {
        val user = login("empty")
        val portfolio = portfolio("RCC-EMPTY")
        val broker = broker("RCC-EMPTY-B", user)

        assertRefused(confirmed(portfolio, broker, "rcc-empty-upload"), "no rows")
    }

    @Test
    fun `should refuse more rows than a statement can hold`() {
        val user = login("many")
        val portfolio = portfolio("RCC-MANY")
        val broker = broker("RCC-MANY-B", user)
        val rows =
            (0..ReconcileService.MAX_ROWS).map {
                ConfirmedRow("rcc-many-$it", TrnType.ADD, BigDecimal.ONE, BigDecimal.ONE)
            }

        assertRefused(
            ConfirmedHoldings(portfolio.id, broker.id, "rcc-many-upload", rows = rows),
            "at most ${ReconcileService.MAX_ROWS} rows"
        )

        assertThat(trnsOf(portfolio)).isEmpty()
    }

    @Test
    fun `should record an upload once, however often it is submitted`() {
        val user = login("once")
        val portfolio = portfolio("RCC-ONCE")
        val first = asset("RCCE1")
        val second = asset("RCCE2")
        val broker = broker("RCC-ONCE-B", user)
        val submission = confirmed(portfolio, broker, "rcc-once-upload", add(first, "5"))
        reconcileCommitService.commit(submission)
        val recorded = trnsOf(portfolio).map { it.id }

        assertRefused(submission, "already recorded")
        // The upload is what is recorded once, not each of its rows.
        assertRefused(confirmed(portfolio, broker, "rcc-once-upload", add(second, "5")), "already recorded")

        assertThat(trnsOf(portfolio).map { it.id }).containsExactlyElementsOf(recorded)
        assertThat(heldAt(broker, portfolio, first)).isEqualByComparingTo("5")
    }

    @Test
    fun `should record an upload whose id and asset another user has already recorded`() {
        val other = login("same-other")
        val theirPortfolio = portfolio("RCC-SAME-O")
        val asset = asset("RCCS1")
        reconcileCommitService.commit(
            confirmed(theirPortfolio, broker("RCC-SAME-OB", other), "rcc-same-upload", add(asset, "1"))
        )
        val user = login("same-caller")
        val portfolio = portfolio("RCC-SAME-C")
        val broker = broker("RCC-SAME-CB", user)

        reconcileCommitService.commit(confirmed(portfolio, broker, "rcc-same-upload", add(asset, "2")))

        assertThat(heldAt(broker, portfolio, asset)).isEqualByComparingTo("2")
    }

    @Test
    fun `should record an upload whose id another user recorded into the caller's shared portfolio`() {
        val other = login("shared-other")
        val user = login("shared-caller")
        val portfolio = portfolio("RCC-SHARED")
        val asset = asset("RCCH1")
        trnRepository.save(
            Trn(
                trnType = TrnType.ADD,
                asset = asset,
                quantity = BigDecimal.ONE,
                portfolio = portfolio,
                broker = broker("RCC-SHARED-OB", other),
                tradeDate = LocalDate.of(2026, 1, 1),
                callerRef = CallerRef("SCREENSHOT", "rcc-shared-upload", "${other.id}:${asset.id}")
            )
        )
        val broker = broker("RCC-SHARED-CB", user)

        reconcileCommitService.commit(confirmed(portfolio, broker, "rcc-shared-upload", add(asset, "2")))

        assertThat(heldAt(broker, portfolio, asset)).isEqualByComparingTo("2")
    }

    @Test
    fun `should refuse a trade date after today`() {
        val user = login("future")
        val portfolio = portfolio("RCC-FUTURE")
        val broker = broker("RCC-FUTURE-B", user)
        val tomorrow = DateUtils().date.plusDays(1)

        assertRefused(
            confirmed(portfolio, broker, "rcc-future-upload", add(asset("RCCF1"), "1")).copy(tradeDate = tomorrow),
            "after today"
        )

        assertThat(trnsOf(portfolio)).isEmpty()
    }

    @Test
    fun `should create no cash transaction`() {
        val user = login("cash")
        val portfolio = portfolio("RCC-CASH")
        val added = asset("RCCX1")
        val reduced = asset("RCCX2")
        val broker = broker("RCC-CASH-B", user)
        bought(portfolio, broker, reduced, "10")

        val result =
            reconcileCommitService.commit(
                confirmed(portfolio, broker, "rcc-cash-upload", add(added, "3", "90"), reduce(reduced, "4"))
            )

        // The seeded BUY plus one transaction per row, across every portfolio the caller owns.
        val stored = trnsOwnedBy(user)
        assertThat(stored).extracting("trnType").containsExactlyInAnyOrder(TrnType.BUY, TrnType.ADD, TrnType.REDUCE)
        val recorded = stored.filter { it.callerRef?.provider == "SCREENSHOT" }
        assertThat(recorded).hasSize(2).allSatisfy {
            assertThat(it.cashAsset).isNull()
            assertThat(it.cashCurrency).isNull()
            assertThat(it.cashAmount).isEqualByComparingTo("0")
        }
        assertThat(result.warnings).isEmpty()
    }
}