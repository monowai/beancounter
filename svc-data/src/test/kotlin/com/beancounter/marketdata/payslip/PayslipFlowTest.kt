package com.beancounter.marketdata.payslip

import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.enrichment.DefaultEnricher
import com.beancounter.marketdata.assets.enrichment.EnrichmentFactory
import com.beancounter.marketdata.assets.figi.FigiProxy
import com.beancounter.marketdata.assets.privateasset.PolicyType
import com.beancounter.marketdata.assets.privateasset.PrivateAssetConfig
import com.beancounter.marketdata.assets.privateasset.PrivateAssetConfigRepository
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.registration.UserPreferencesRepository
import com.beancounter.marketdata.registration.UserPreferencesService
import com.beancounter.marketdata.trn.TrnRepository
import com.beancounter.marketdata.utils.BcMvcHelper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.messaging.MessagingException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The scheduled half of recurring payslips against the H2 test database: the daily job
 * publishes a due message for each row whose next pay date has arrived, and the consumer
 * records the transactions svc-retire calculated, then advances the row by one month.
 */
@SpringMvcDbTest
class PayslipFlowTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var figiProxy: FigiProxy

    @MockitoBean
    private lateinit var fxTransactions: FxTransactions

    @MockitoBean
    private lateinit var streamBridge: StreamBridge

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var enrichmentFactory: EnrichmentFactory

    @Autowired
    private lateinit var defaultEnricher: DefaultEnricher

    @Autowired
    private lateinit var recurringPayslipRepository: RecurringPayslipRepository

    @Autowired
    private lateinit var privateAssetConfigRepository: PrivateAssetConfigRepository

    @Autowired
    private lateinit var systemUserRepository: SystemUserRepository

    @Autowired
    private lateinit var userPreferencesService: UserPreferencesService

    @Autowired
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    @Autowired
    private lateinit var trnRepository: TrnRepository

    @Autowired
    private lateinit var payslipDueService: PayslipDueService

    @Autowired
    private lateinit var payslipDueProducer: PayslipDueProducer

    @Autowired
    private lateinit var payslipRecorder: PayslipRecorder

    @Autowired
    private lateinit var dateUtils: DateUtils

    private val today: LocalDate get() = dateUtils.date

    @BeforeEach
    fun configure() {
        enrichmentFactory.register(defaultEnricher)
        // The h2db profile disables the stream; the producer's gate is opened so the send is observable.
        payslipDueProducer.streamEnabled = true
        clearInvocations(streamBridge)
    }

    /** A registered user with a portfolio, an SGD bank account and, optionally, a CPF policy. */
    private inner class Payee(
        val name: String
    ) {
        val helper = BcMvcHelper(mockMvc, mockAuthConfig.getUserToken(SystemUser(name, "$name@testing.com")))

        init {
            helper.registerUser()
        }

        val portfolio: Portfolio = helper.portfolio(PortfolioInput(name.uppercase(), name, currency = SGD.code))
        val owner: SystemUser = systemUserRepository.findById(portfolio.owner.id).orElseThrow()
        val cash: Asset =
            helper.asset(AssetRequest(AssetInput.toAccount(SGD, "$name-BANK", "$name bank", owner = "")))

        fun cpf(): Asset {
            val policy = helper.asset(AssetRequest(AssetInput.toPolicy(SGD, "$name-CPF", "$name CPF", owner = "")))
            privateAssetConfigRepository.save(PrivateAssetConfig(assetId = policy.id, policyType = PolicyType.CPF))
            return policy
        }

        fun born(
            year: Int,
            month: Int
        ) {
            val preferences = userPreferencesService.getOrCreate(owner)
            preferences.yearOfBirth = year
            preferences.monthOfBirth = month
            userPreferencesRepository.save(preferences)
        }

        fun schedule(
            nextPayDate: LocalDate,
            dayOfMonth: Int = nextPayDate.dayOfMonth,
            tax: String = "500"
        ): RecurringPayslip =
            recurringPayslipRepository.save(
                RecurringPayslip(
                    ownerId = owner.id,
                    portfolioId = portfolio.id,
                    cashAssetId = cash.id,
                    grossSalary = BigDecimal("10000"),
                    tax = BigDecimal(tax),
                    dayOfMonth = dayOfMonth,
                    nextPayDate = nextPayDate
                )
            )

        fun trns(): List<com.beancounter.common.model.Trn> =
            trnRepository.findByPortfolioId(portfolio.id, LocalDate.of(2100, 1, 1), TrnStatus.SETTLED).toList()
    }

    private fun published(): List<PayslipDue> {
        val captor = ArgumentCaptor.forClass(Any::class.java)
        verify(streamBridge, atLeast(0)).send(eq(PayslipDueProducer.BINDING), captor.capture())
        return captor.allValues.filterIsInstance<PayslipDue>()
    }

    private fun publishedFor(row: RecurringPayslip): List<PayslipDue> {
        payslipDueService.publishDue()
        return published().filter { it.recurringPayslipId == row.id }
    }

    private fun legs(
        row: RecurringPayslip,
        payee: Payee,
        payDate: LocalDate = row.nextPayDate,
        portfolioId: String = row.portfolioId,
        batch: String = row.id
    ): PayslipTrns {
        fun leg(
            number: Int,
            trnType: TrnType,
            amount: String,
            cashAmount: String
        ) = TrnInput(
            callerRef = CallerRef("PAYSLIP", batch, "$payDate-$number"),
            assetId = payee.cash.id,
            cashAssetId = payee.cash.id,
            trnType = trnType,
            quantity = BigDecimal(amount),
            price = BigDecimal.ONE,
            tradeCurrency = SGD.code,
            cashCurrency = SGD.code,
            tradeAmount = BigDecimal(amount),
            cashAmount = BigDecimal(cashAmount),
            tradeDate = payDate,
            status = TrnStatus.SETTLED,
            comments = "leg $number"
        )
        return PayslipTrns(
            recurringPayslipId = row.id,
            portfolioId = portfolioId,
            payDate = payDate,
            trns =
                listOf(
                    leg(1, TrnType.INCOME, "10000", "10000"),
                    leg(3, TrnType.DEDUCTION, "500", "-500")
                )
        )
    }

    private fun nextPayDate(row: RecurringPayslip): LocalDate =
        recurringPayslipRepository.findById(row.id).orElseThrow().nextPayDate

    @Test
    fun `should publish a due payslip with the cash currency, the CPF asset and the birth month`() {
        val payee = Payee("pf-due-cpf")
        val cpf = payee.cpf()
        payee.born(1970, 6)
        val row = payee.schedule(today)

        val due = publishedFor(row)

        assertThat(due).hasSize(1)
        assertThat(due.single())
            .usingRecursiveComparison()
            .withComparatorForType(BigDecimal::compareTo, BigDecimal::class.java)
            .isEqualTo(
                PayslipDue(
                    recurringPayslipId = row.id,
                    ownerId = payee.owner.id,
                    portfolioId = payee.portfolio.id,
                    payDate = today,
                    grossSalary = BigDecimal("10000"),
                    tax = BigDecimal("500"),
                    currency = SGD.code,
                    cashAssetId = payee.cash.id,
                    cpfAssetId = cpf.id,
                    yearOfBirth = 1970,
                    monthOfBirth = 6
                )
            )
    }

    @Test
    fun `should publish nulls for the CPF asset and birth month when the owner has none`() {
        val payee = Payee("pf-due-plain")
        val row = payee.schedule(today.minusDays(3), tax = "0")

        val due = publishedFor(row).single()

        assertThat(due.payDate).isEqualTo(today.minusDays(3))
        assertThat(due.tax).isEqualByComparingTo("0")
        assertThat(due.currency).isEqualTo(SGD.code)
        assertThat(due.cpfAssetId).isNull()
        assertThat(due.yearOfBirth).isNull()
        assertThat(due.monthOfBirth).isNull()
    }

    @Test
    fun `should publish one month per run and leave the next pay date alone`() {
        val payee = Payee("pf-due-catchup")
        val row = payee.schedule(today.minusMonths(2))

        assertThat(publishedFor(row).map { it.payDate }).containsExactly(today.minusMonths(2))
        assertThat(nextPayDate(row)).isEqualTo(today.minusMonths(2))
    }

    @Test
    fun `should publish nothing for a payslip not yet due`() {
        val payee = Payee("pf-due-future")
        val row = payee.schedule(today.plusDays(1))

        assertThat(publishedFor(row)).isEmpty()
    }

    @Test
    fun `should publish the remaining payslips when one send fails`() {
        val first = Payee("pf-due-fail-1").schedule(today)
        val second = Payee("pf-due-fail-2").schedule(today)
        whenever(streamBridge.send(eq(PayslipDueProducer.BINDING), any()))
            .thenThrow(MessagingException("broker down"))
            .thenReturn(true)

        assertThatCode { payslipDueService.publishDue() }.doesNotThrowAnyException()

        assertThat(published().map { it.recurringPayslipId }).contains(first.id, second.id)
    }

    @Test
    fun `should drop transactions whose first leg has no caller reference`() {
        val payee = Payee("pf-no-ref")
        val row = payee.schedule(LocalDate.of(2026, 10, 12))

        payslipRecorder.record(legs(row, payee, batch = ""))

        assertThat(payee.trns()).isEmpty()
        assertThat(nextPayDate(row)).isEqualTo(LocalDate.of(2026, 10, 12))
    }

    @Test
    fun `should record the legs with their caller references and advance one month`() {
        val payee = Payee("pf-record")
        val row = payee.schedule(LocalDate.of(2026, 1, 31), dayOfMonth = 31)

        payslipRecorder.record(legs(row, payee))

        val trns = payee.trns()
        assertThat(trns.map { it.callerRef?.callerId })
            .containsExactlyInAnyOrder("2026-01-31-1", "2026-01-31-3")
        assertThat(trns.map { it.callerRef?.batch }.toSet()).containsExactly(row.id)
        assertThat(trns.map { it.callerRef?.provider }.toSet()).containsExactly("PAYSLIP")
        assertThat(trns.map { it.trnType }).containsExactlyInAnyOrder(TrnType.INCOME, TrnType.DEDUCTION)
        assertThat(nextPayDate(row)).isEqualTo(LocalDate.of(2026, 2, 28))
    }

    @Test
    fun `should save nothing more and not advance again when the message is redelivered`() {
        val payee = Payee("pf-redeliver")
        val row = payee.schedule(LocalDate.of(2026, 3, 15))
        val message = legs(row, payee)
        payslipRecorder.record(message)

        payslipRecorder.record(message)

        assertThat(payee.trns()).hasSize(2)
        assertThat(nextPayDate(row)).isEqualTo(LocalDate.of(2026, 4, 15))
    }

    @Test
    fun `should advance without saving again when the legs are already recorded`() {
        val payee = Payee("pf-recorded")
        val row = payee.schedule(LocalDate.of(2026, 5, 10))
        val message = legs(row, payee)
        payslipRecorder.record(message)
        // The legs are recorded but the row still points at their pay date.
        recurringPayslipRepository.save(
            recurringPayslipRepository.findById(row.id).orElseThrow().copy(nextPayDate = row.nextPayDate)
        )

        payslipRecorder.record(message)

        assertThat(payee.trns()).hasSize(2)
        assertThat(nextPayDate(row)).isEqualTo(LocalDate.of(2026, 6, 10))
    }

    @Test
    fun `should drop the transactions of a payslip that was stopped`() {
        val payee = Payee("pf-stopped")
        val row = payee.schedule(LocalDate.of(2026, 7, 1))
        recurringPayslipRepository.delete(row)

        payslipRecorder.record(legs(row, payee))

        assertThat(payee.trns()).isEmpty()
    }

    @Test
    fun `should drop transactions for a pay date other than the next one`() {
        val payee = Payee("pf-stale")
        val row = payee.schedule(LocalDate.of(2026, 8, 20))

        payslipRecorder.record(legs(row, payee, payDate = LocalDate.of(2026, 7, 20)))

        assertThat(payee.trns()).isEmpty()
        assertThat(nextPayDate(row)).isEqualTo(LocalDate.of(2026, 8, 20))
    }

    @Test
    fun `should drop transactions addressed to a different portfolio`() {
        val payee = Payee("pf-elsewhere")
        val other = Payee("pf-elsewhere-other")
        val row = payee.schedule(LocalDate.of(2026, 9, 5))

        payslipRecorder.record(legs(row, payee, portfolioId = other.portfolio.id))

        assertThat(payee.trns()).isEmpty()
        assertThat(other.trns()).isEmpty()
        assertThat(nextPayDate(row)).isEqualTo(LocalDate.of(2026, 9, 5))
    }
}