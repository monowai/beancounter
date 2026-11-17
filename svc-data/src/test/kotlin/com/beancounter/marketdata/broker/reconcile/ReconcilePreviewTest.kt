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
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.broker.BrokerConfig
import com.beancounter.marketdata.broker.BrokerRepository
import com.beancounter.marketdata.broker.GlobalBroker
import com.beancounter.marketdata.broker.GlobalBrokerRepository
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Previewing a brokerage statement: which firm and which of the caller's brokers it
 * belongs to, and the transaction each shown holding would need. Nothing is recorded.
 */
@SpringMvcDbTest
class ReconcilePreviewTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var reconcileService: ReconcileService

    @Autowired
    private lateinit var brokerRepository: BrokerRepository

    @Autowired
    private lateinit var globalBrokerRepository: GlobalBrokerRepository

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
        val email = "rcn-$name@test.com"
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
            .data[code]!!

    private fun firm(
        code: String,
        name: String,
        config: BrokerConfig = BrokerConfig(market = NASDAQ.code)
    ): GlobalBroker = globalBrokerRepository.save(GlobalBroker(code = code, name = name, config = config))

    private fun trade(
        trnType: TrnType,
        portfolio: Portfolio,
        broker: Broker,
        asset: Asset,
        quantity: String
    ) {
        trnRepository.save(
            Trn(
                trnType = trnType,
                asset = asset,
                quantity = BigDecimal(quantity),
                portfolio = portfolio,
                broker = broker,
                tradeDate = LocalDate.of(2026, 1, 1)
            )
        )
    }

    private fun shown(
        code: String,
        quantity: String?,
        market: String? = NASDAQ.code
    ) = ShownHolding(code = code, market = market, quantity = quantity?.let { BigDecimal(it) })

    private fun trnCount(portfolio: Portfolio): Int = trnRepository.findAll().count { it.portfolio.id == portfolio.id }

    @Test
    fun `should not find a portfolio the caller may not view, before looking at the broker`() {
        login("p-owner")
        val hidden = portfolio("RCN-HIDDEN")
        login("p-stranger")

        assertThatThrownBy {
            reconcileService.preview(ShownHoldings(portfolioId = hidden.id, brokerId = "rcn-no-such-broker"))
        }.isInstanceOf(NotFoundException::class.java)
            .hasMessage("Portfolio not found: ${hidden.id}")
    }

    @Test
    fun `should name the firm by its name, code or alias, ignoring case and outer spaces`() {
        login("firm-named")
        val portfolio = portfolio("RCN-FIRM")
        val firm =
            firm(
                "RCN-NAMED",
                "Rcn Named Securities",
                BrokerConfig(aliases = listOf("Rcn Named Sec", "RNS Online"), market = NASDAQ.code)
            )

        for (printed in listOf("  rcn named SECURITIES ", "rcn-named", " rns online")) {
            val preview = reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, brokerName = printed))
            assertThat(preview.globalBroker?.id).describedAs(printed).isEqualTo(firm.id)
        }
    }

    @Test
    fun `should name no firm when the printed name is blank, missing or unknown`() {
        login("firm-none")
        val portfolio = portfolio("RCN-NOFIRM")
        firm("RCN-SOME", "Rcn Some Firm")

        for (printed in listOf(null, "   ", "Rcn Nobody Knows")) {
            val preview = reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, brokerName = printed))
            assertThat(preview.globalBroker).describedAs("$printed").isNull()
        }
    }

    @Test
    fun `should prefer the firm the caller's broker is linked to over the printed name`() {
        val user = login("firm-linked")
        val portfolio = portfolio("RCN-LINKED")
        val printed = firm("RCN-PRINTED", "Rcn Printed Firm")
        val linked = firm("RCN-OWNFIRM", "Rcn Own Firm")
        val broker = brokerRepository.save(Broker(name = "RCN-LINKED-B", owner = user, globalBrokerId = linked.id))

        val preview =
            reconcileService.preview(
                ShownHoldings(portfolioId = portfolio.id, brokerId = broker.id, brokerName = printed.name)
            )

        assertThat(preview.broker).isEqualTo(MatchedBroker(broker.id, "RCN-LINKED-B", null))
        assertThat(preview.globalBroker?.id).isEqualTo(linked.id)
    }

    @Test
    fun `should not find a broker id that belongs to another user`() {
        val other = login("id-other")
        val theirs = brokerRepository.save(Broker(name = "RCN-THEIRS", owner = other))
        login("id-caller")
        val portfolio = portfolio("RCN-BYID")

        assertThatThrownBy {
            reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, brokerId = theirs.id))
        }.isInstanceOf(NotFoundException::class.java)
            .hasMessage("Broker not found: ${theirs.id}")
    }

    @Test
    fun `should match the caller's broker whose account number ends with the visible digits`() {
        val user = login("digits")
        val portfolio = portfolio("RCN-DIGITS")
        val match = brokerRepository.save(Broker(name = "RCN-D-MATCH", owner = user, accountNumber = "U-1234-482"))
        brokerRepository.save(Broker(name = "RCN-D-OTHER", owner = user, accountNumber = "U-4820-001"))
        brokerRepository.save(Broker(name = "RCN-D-NONE", owner = user))

        val preview = reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, accountNumber = "***4-82"))

        assertThat(preview.broker).isEqualTo(MatchedBroker(match.id, "RCN-D-MATCH", "U-1234-482"))
    }

    @Test
    fun `should match the caller's only broker linked to the named firm`() {
        val user = login("by-firm")
        val portfolio = portfolio("RCN-BYFIRM")
        val firm = firm("RCN-BYFIRM", "Rcn By Firm")
        val linked = brokerRepository.save(Broker(name = "RCN-F-LINKED", owner = user, globalBrokerId = firm.id))
        brokerRepository.save(Broker(name = "RCN-F-UNLINKED", owner = user))

        val preview = reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, brokerName = "rcn by firm"))

        assertThat(preview.broker?.id).isEqualTo(linked.id)
        assertThat(preview.globalBroker?.id).isEqualTo(firm.id)
    }

    @Test
    fun `should use the firm to choose between two brokers whose digits both fit`() {
        val user = login("both-signals")
        val portfolio = portfolio("RCN-BOTH")
        val firm = firm("RCN-BOTH", "Rcn Both Signals")
        val linked =
            brokerRepository.save(
                Broker(name = "RCN-B-LINKED", owner = user, accountNumber = "77482", globalBrokerId = firm.id)
            )
        brokerRepository.save(Broker(name = "RCN-B-PLAIN", owner = user, accountNumber = "99482"))
        val request = ShownHoldings(portfolioId = portfolio.id, accountNumber = "482")

        assertThat(reconcileService.preview(request).broker).isNull()
        assertThat(reconcileService.preview(request.copy(brokerName = firm.name)).broker?.id).isEqualTo(linked.id)
    }

    @Test
    fun `should match no broker when two of the caller's brokers are linked to the firm`() {
        val user = login("ambiguous")
        val portfolio = portfolio("RCN-AMBIG")
        val firm = firm("RCN-AMBIG", "Rcn Ambiguous")
        brokerRepository.save(Broker(name = "RCN-A-ONE", owner = user, globalBrokerId = firm.id))
        brokerRepository.save(Broker(name = "RCN-A-TWO", owner = user, globalBrokerId = firm.id))

        val preview = reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, brokerName = firm.name))

        assertThat(preview.broker).isNull()
        assertThat(preview.globalBroker?.id).isEqualTo(firm.id)
    }

    @Test
    fun `should match no broker without digits or a linked firm, even when the caller has only one`() {
        val user = login("no-signal")
        val portfolio = portfolio("RCN-NOSIGNAL")
        val firm = firm("RCN-NOSIGNAL", "Rcn No Signal")
        brokerRepository.save(Broker(name = "RCN-N-ONLY", owner = user, accountNumber = "555"))

        val nothingGiven = reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, accountNumber = "n/a"))
        val firmNotLinked = reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, brokerName = firm.name))

        assertThat(nothingGiven.broker).isNull()
        assertThat(firmNotLinked.broker).isNull()
        assertThat(firmNotLinked.globalBroker?.id).isEqualTo(firm.id)
    }

    @Test
    fun `should never match another user's broker`() {
        val firm = firm("RCN-PRIVATE", "Rcn Private")
        val other = login("private-other")
        brokerRepository.save(
            Broker(name = "RCN-P-THEIRS", owner = other, accountNumber = "000482", globalBrokerId = firm.id)
        )
        login("private-caller")
        val portfolio = portfolio("RCN-PRIVATE")

        val preview =
            reconcileService.preview(
                ShownHoldings(portfolioId = portfolio.id, brokerName = firm.name, accountNumber = "482")
            )

        assertThat(preview.broker).isNull()
    }

    @Test
    fun `should ask for a quantity before anything else`() {
        login("needs-quantity")
        val portfolio = portfolio("RCN-NEEDQ")

        val row =
            reconcileService
                .preview(ShownHoldings(portfolioId = portfolio.id, rows = listOf(shown("RCNQ1", null, market = null))))
                .rows
                .single()

        assertThat(row.status).isEqualTo(RowStatus.NEEDS_QUANTITY)
        assertThat(row.shown).isNull()
        assertThat(row.trnType).isNull()
        assertThat(row.quantity).isNull()
    }

    @Test
    fun `should ask for a quantity when the shown one is negative, and propose nothing`() {
        login("negative-quantity")
        val portfolio = portfolio("RCN-NEGQ")
        asset("RCNNEG1")

        val row =
            reconcileService
                .preview(ShownHoldings(portfolioId = portfolio.id, rows = listOf(shown("RCNNEG1", "-5"))))
                .rows
                .single()

        assertThat(row.status).isEqualTo(RowStatus.NEEDS_QUANTITY)
        assertThat(row.trnType).isNull()
        assertThat(row.quantity).isNull()
    }

    @Test
    fun `should ask for a market when neither the row nor the firm gives one`() {
        login("needs-market")
        val portfolio = portfolio("RCN-NEEDM")
        asset("RCNM1")
        val firm = firm("RCN-NOMKT", "Rcn No Market", BrokerConfig())
        val request =
            ShownHoldings(
                portfolioId = portfolio.id,
                brokerName = firm.name,
                rows = listOf(shown("RCNM1", "5", market = " "))
            )

        val row = reconcileService.preview(request).rows.single()

        assertThat(row.status).isEqualTo(RowStatus.NEEDS_MARKET)
        assertThat(row.market).isNull()
        assertThat(row.assetId).isNull()
        assertThat(row.trnType).isNull()
    }

    @Test
    fun `should fall back to the firm's market when the row has none`() {
        login("firm-market")
        val portfolio = portfolio("RCN-FIRMMKT")
        val asset = asset("RCNM2")
        val firm = firm("RCN-MKT", "Rcn With Market")
        val request =
            ShownHoldings(
                portfolioId = portfolio.id,
                brokerName = firm.name,
                rows = listOf(shown("rcnm2", "5", market = null))
            )

        val row = reconcileService.preview(request).rows.single()

        assertThat(row.market).isEqualTo(NASDAQ.code)
        assertThat(row.assetId).isEqualTo(asset.id)
        assertThat(row.assetName).isEqualTo(asset.name)
        assertThat(row.status).isEqualTo(RowStatus.NEEDS_COST)
    }

    @Test
    fun `should leave an unknown asset or market unresolved without creating the asset`() {
        login("unresolved")
        val portfolio = portfolio("RCN-UNRES")
        val request =
            ShownHoldings(
                portfolioId = portfolio.id,
                rows = listOf(shown("RCNNOSUCH", "5"), shown("RCNNOSUCH", "5", market = "RCN-NO-MARKET"))
            )

        val rows = reconcileService.preview(request).rows

        assertThat(rows).extracting("status").containsExactly(RowStatus.UNRESOLVED, RowStatus.UNRESOLVED)
        assertThat(rows).extracting("assetId").containsOnlyNulls()
        assertThat(rows).extracting("trnType").containsOnlyNulls()
        assertThat(assetFinder.findLocally(AssetInput(NASDAQ.code, "RCNNOSUCH"))).isNull()
    }

    @Test
    fun `should propose nothing when the shown quantity equals the held quantity`() {
        val user = login("unchanged")
        val portfolio = portfolio("RCN-SAME")
        val asset = asset("RCNU1")
        val broker = brokerRepository.save(Broker(name = "RCN-SAME-B", owner = user))
        trade(TrnType.BUY, portfolio, broker, asset, "100")
        val request =
            ShownHoldings(portfolioId = portfolio.id, brokerId = broker.id, rows = listOf(shown("RCNU1", "100.00")))

        val row = reconcileService.preview(request).rows.single()

        assertThat(row.status).isEqualTo(RowStatus.UNCHANGED)
        assertThat(row.held).isEqualByComparingTo("100")
        assertThat(row.trnType).isNull()
        assertThat(row.quantity).isNull()
        assertThat(row.cost).isNull()
    }

    @Test
    fun `should add the shortfall at its share of the shown cost basis`() {
        val user = login("add-basis")
        val portfolio = portfolio("RCN-ADD")
        val asset = asset("RCNA1")
        val broker = brokerRepository.save(Broker(name = "RCN-ADD-B", owner = user))
        trade(TrnType.BUY, portfolio, broker, asset, "1")
        // Cost per share is ignored when the statement prints the total.
        val shown = shown("RCNA1", "3").copy(costBasis = BigDecimal("100"), costPerShare = BigDecimal("999"))

        val row =
            reconcileService
                .preview(ShownHoldings(portfolioId = portfolio.id, brokerId = broker.id, rows = listOf(shown)))
                .rows
                .single()

        assertThat(row.status).isEqualTo(RowStatus.READY)
        assertThat(row.trnType).isEqualTo(TrnType.ADD)
        assertThat(row.assetId).isEqualTo(asset.id)
        assertThat(row.shown).isEqualByComparingTo("3")
        assertThat(row.held).isEqualByComparingTo("1")
        assertThat(row.quantity).isEqualByComparingTo("2")
        // 100 x 2 / 3 = 66.666..., rounded half up to cents.
        assertThat(row.cost).isEqualTo(BigDecimal("66.67"))
    }

    @Test
    fun `should cost an add from the cost per share when no cost basis is shown`() {
        val user = login("add-per-share")
        val portfolio = portfolio("RCN-ADDPS")
        val asset = asset("RCNA2")
        val broker = brokerRepository.save(Broker(name = "RCN-ADDPS-B", owner = user))
        trade(TrnType.BUY, portfolio, broker, asset, "40")
        val shown = shown("RCNA2", "100").copy(costPerShare = BigDecimal("12.5"))

        val row =
            reconcileService
                .preview(ShownHoldings(portfolioId = portfolio.id, brokerId = broker.id, rows = listOf(shown)))
                .rows
                .single()

        assertThat(row.status).isEqualTo(RowStatus.READY)
        assertThat(row.trnType).isEqualTo(TrnType.ADD)
        assertThat(row.quantity).isEqualByComparingTo("60")
        assertThat(row.cost).isEqualByComparingTo("750")
    }

    @Test
    fun `should ask for a cost when an add has none, and hold nothing when no broker matched`() {
        login("needs-cost")
        val portfolio = portfolio("RCN-NEEDC")
        asset("RCNC1")

        val row =
            reconcileService
                .preview(ShownHoldings(portfolioId = portfolio.id, rows = listOf(shown("RCNC1", "7"))))
                .rows
                .single()

        assertThat(row.status).isEqualTo(RowStatus.NEEDS_COST)
        assertThat(row.held).isEqualByComparingTo("0")
        assertThat(row.trnType).isEqualTo(TrnType.ADD)
        assertThat(row.quantity).isEqualByComparingTo("7")
        assertThat(row.cost).isNull()
    }

    @Test
    fun `should reduce the excess without a cost`() {
        val user = login("reduce")
        val portfolio = portfolio("RCN-REDUCE")
        val asset = asset("RCNR1")
        val broker = brokerRepository.save(Broker(name = "RCN-REDUCE-B", owner = user))
        trade(TrnType.BUY, portfolio, broker, asset, "100")
        val shown = shown("RCNR1", "30").copy(costBasis = BigDecimal("300"))

        val row =
            reconcileService
                .preview(ShownHoldings(portfolioId = portfolio.id, brokerId = broker.id, rows = listOf(shown)))
                .rows
                .single()

        assertThat(row.status).isEqualTo(RowStatus.READY)
        assertThat(row.trnType).isEqualTo(TrnType.REDUCE)
        assertThat(row.quantity).isEqualByComparingTo("70")
        assertThat(row.cost).isNull()
    }

    @Test
    fun `should warn when quantity times price is further from the market value than the printed rounding allows`() {
        login("warn-value")
        val portfolio = portfolio("RCN-WARNMV")
        val msft = shown("RCNW1", "10").copy(price = BigDecimal("523.88"))
        val rows =
            listOf(
                msft.copy(marketValue = BigDecimal("5238.80")),
                // A vision model misread one digit of the market value.
                msft.copy(marketValue = BigDecimal("5236.80")),
                // A price printed to the cent, the market value worked from the unrounded price.
                shown("RCNW1", "1000").copy(price = BigDecimal("1.23"), marketValue = BigDecimal("1234.50")),
                shown("RCNW1", "54").copy(price = BigDecimal("4.505"), marketValue = BigDecimal("243.27")),
                shown("RCNW1", "10").copy(price = BigDecimal("10"), marketValue = BigDecimal("101")),
                msft,
                msft.copy(price = null, marketValue = BigDecimal("500"))
            )

        val preview = reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, rows = rows))

        val mismatch = listOf(RowWarning.MARKET_VALUE_MISMATCH)
        assertThat(preview.rows.map { it.warnings })
            .containsExactly(emptyList(), mismatch, emptyList(), emptyList(), mismatch, emptyList(), emptyList())
    }

    @Test
    fun `should warn when market value less cost basis is more than a cent away from the gain or loss`() {
        login("warn-gain")
        val portfolio = portfolio("RCN-WARNGL")
        val valued = shown("RCNW2", null).copy(marketValue = BigDecimal("1000"), costBasis = BigDecimal("800"))
        val rows =
            listOf(
                valued.copy(gainLoss = BigDecimal("200")),
                valued.copy(gainLoss = BigDecimal("200.01")),
                valued.copy(gainLoss = BigDecimal("201.00")),
                valued.copy(gainLoss = BigDecimal("-200")),
                valued,
                valued.copy(costBasis = null, gainLoss = BigDecimal("5"))
            )

        val preview = reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, rows = rows))

        val mismatch = listOf(RowWarning.GAIN_LOSS_MISMATCH)
        assertThat(preview.rows.map { it.warnings })
            .containsExactly(emptyList(), emptyList(), mismatch, mismatch, emptyList(), emptyList())
        // A warning does not depend on the row's status.
        assertThat(preview.rows).extracting("status").containsOnly(RowStatus.NEEDS_QUANTITY)
    }

    @Test
    fun `should check the gain or loss against cost per share x quantity when no cost basis is shown`() {
        login("warn-gain-per-share")
        val portfolio = portfolio("RCN-WARNGLPS")
        val valued = shown("RCNW3", "10").copy(marketValue = BigDecimal("1000"), costPerShare = BigDecimal("80"))
        val rows =
            listOf(
                valued.copy(gainLoss = BigDecimal("200")),
                valued.copy(gainLoss = BigDecimal("5")),
                valued.copy(quantity = null, gainLoss = BigDecimal("5")),
                // An average cost printed to the cent, the gain worked from the unrounded cost.
                valued.copy(
                    quantity = BigDecimal("1000"),
                    marketValue = BigDecimal("2000"),
                    costPerShare = BigDecimal("1.23"),
                    gainLoss = BigDecimal("765.50")
                )
            )

        val preview = reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, rows = rows))

        assertThat(preview.rows.map { it.warnings })
            .containsExactly(emptyList(), listOf(RowWarning.GAIN_LOSS_MISMATCH), emptyList(), emptyList())
    }

    @Test
    fun `should list what the broker holds that the statement does not show, and propose nothing for it`() {
        val user = login("not-shown")
        val portfolio = portfolio("RCN-NOTSHOWN")
        val shownAsset = asset("RCNN1")
        val absent = asset("RCNN2")
        val soldOut = asset("RCNN3")
        val broker = brokerRepository.save(Broker(name = "RCN-NOTSHOWN-B", owner = user))
        trade(TrnType.BUY, portfolio, broker, shownAsset, "100")
        trade(TrnType.BUY, portfolio, broker, absent, "50")
        trade(TrnType.BUY, portfolio, broker, soldOut, "20")
        trade(TrnType.SELL, portfolio, broker, soldOut, "20")
        val rows = listOf(shown("RCNN1", "100"))

        val preview =
            reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, brokerId = broker.id, rows = rows))
        val unmatched = reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, rows = rows))

        val notShown = preview.notShown.single()
        assertThat(notShown.assetId).isEqualTo(absent.id)
        assertThat(notShown.code).isEqualTo("RCNN2")
        assertThat(notShown.market).isEqualTo(NASDAQ.code)
        assertThat(notShown.quantity).isEqualByComparingTo("50")
        assertThat(preview.rows).extracting("assetId").containsExactly(shownAsset.id)
        assertThat(unmatched.notShown).isEmpty()
    }

    @Test
    fun `should record nothing when previewing`() {
        val user = login("read-only")
        val portfolio = portfolio("RCN-READONLY")
        val asset = asset("RCNO1")
        asset("RCNO2")
        val broker = brokerRepository.save(Broker(name = "RCN-READONLY-B", owner = user))
        trade(TrnType.BUY, portfolio, broker, asset, "10")
        val before = trnCount(portfolio)
        val rows =
            listOf(
                shown("RCNO1", "25").copy(costBasis = BigDecimal("250")),
                shown("RCNO2", "5").copy(costPerShare = BigDecimal("2")),
                shown("RCNO1", "1")
            )

        val preview =
            reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, brokerId = broker.id, rows = rows))

        assertThat(preview.rows).extracting("trnType").containsExactly(TrnType.ADD, TrnType.ADD, TrnType.REDUCE)
        assertThat(before).isEqualTo(1)
        assertThat(trnCount(portfolio)).isEqualTo(before)
    }

    @Test
    fun `should refuse more rows than a statement can hold`() {
        login("too-many")
        val portfolio = portfolio("RCN-TOOMANY")
        val rows = List(ReconcileService.MAX_ROWS + 1) { shown("RCNX$it", "1") }

        assertThatThrownBy { reconcileService.preview(ShownHoldings(portfolioId = portfolio.id, rows = rows)) }
            .isInstanceOf(BusinessException::class.java)
    }
}