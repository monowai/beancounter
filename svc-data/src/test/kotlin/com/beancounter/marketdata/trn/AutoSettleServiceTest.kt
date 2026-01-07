package com.beancounter.marketdata.trn

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for AutoSettleService - the service that auto-settles PROPOSED
 * EVENT transactions (DIVI, SPLIT) when their tradeDate arrives.
 * TRADE transactions (BUY, SELL) are NOT auto-settled.
 */
class AutoSettleServiceTest {
    private lateinit var trnRepository: TrnRepository
    private lateinit var autoSettleService: AutoSettleService
    private lateinit var dateUtils: DateUtils
    private val today = LocalDate.now()

    private val testPortfolio =
        Portfolio(
            id = "test-portfolio-id",
            code = "TEST",
            name = "Test Portfolio",
            currency = USD,
            base = USD,
            owner = SystemUser("test-user", "test@example.com")
        )

    companion object {
        val EVENT_TYPES = listOf(TrnType.DIVI, TrnType.SPLIT)
    }

    @BeforeEach
    fun setUp() {
        trnRepository = mock(TrnRepository::class.java)
        dateUtils = mock(DateUtils::class.java)
        `when`(dateUtils.date).thenReturn(today)
        autoSettleService = AutoSettleService(trnRepository, dateUtils)
    }

    @Test
    fun `should settle PROPOSED DIVI transactions when tradeDate is today`() {
        // Given: A PROPOSED dividend with tradeDate = today
        val proposedTrn = createProposedTransaction("trn-1", today, TrnType.DIVI)
        `when`(trnRepository.findDueEventTransactions(TrnStatus.PROPOSED, EVENT_TYPES, today))
            .thenReturn(listOf(proposedTrn))

        // When: Auto-settle is triggered
        val result = autoSettleService.autoSettleDueTransactions()

        // Then: The transaction should be settled
        assertThat(result).isEqualTo(1)
        verify(trnRepository).save(proposedTrn)
        assertThat(proposedTrn.status).isEqualTo(TrnStatus.SETTLED)
    }

    @Test
    fun `should settle PROPOSED SPLIT transactions when tradeDate is today`() {
        // Given: A PROPOSED split transaction with tradeDate = today
        val proposedTrn = createProposedTransaction("trn-1", today, TrnType.SPLIT)
        `when`(trnRepository.findDueEventTransactions(TrnStatus.PROPOSED, EVENT_TYPES, today))
            .thenReturn(listOf(proposedTrn))

        // When: Auto-settle is triggered
        val result = autoSettleService.autoSettleDueTransactions()

        // Then: The transaction should be settled
        assertThat(result).isEqualTo(1)
        verify(trnRepository).save(proposedTrn)
        assertThat(proposedTrn.status).isEqualTo(TrnStatus.SETTLED)
    }

    @Test
    fun `should settle PROPOSED transactions when tradeDate is in the past`() {
        // Given: A PROPOSED transaction with tradeDate in the past
        val pastDate = today.minusDays(5)
        val proposedTrn = createProposedTransaction("trn-1", pastDate, TrnType.DIVI)
        `when`(trnRepository.findDueEventTransactions(TrnStatus.PROPOSED, EVENT_TYPES, today))
            .thenReturn(listOf(proposedTrn))

        // When: Auto-settle is triggered
        val result = autoSettleService.autoSettleDueTransactions()

        // Then: The transaction should be settled
        assertThat(result).isEqualTo(1)
        verify(trnRepository).save(proposedTrn)
        assertThat(proposedTrn.status).isEqualTo(TrnStatus.SETTLED)
    }

    @Test
    fun `should settle multiple PROPOSED event transactions`() {
        // Given: Multiple PROPOSED event transactions due for settlement
        val trn1 = createProposedTransaction("trn-1", today, TrnType.DIVI)
        val trn2 = createProposedTransaction("trn-2", today.minusDays(1), TrnType.DIVI)
        val trn3 = createProposedTransaction("trn-3", today.minusDays(10), TrnType.SPLIT)
        `when`(trnRepository.findDueEventTransactions(TrnStatus.PROPOSED, EVENT_TYPES, today))
            .thenReturn(listOf(trn1, trn2, trn3))

        // When: Auto-settle is triggered
        val result = autoSettleService.autoSettleDueTransactions()

        // Then: All transactions should be settled
        assertThat(result).isEqualTo(3)
        assertThat(trn1.status).isEqualTo(TrnStatus.SETTLED)
        assertThat(trn2.status).isEqualTo(TrnStatus.SETTLED)
        assertThat(trn3.status).isEqualTo(TrnStatus.SETTLED)
    }

    @Test
    fun `should not settle transactions when none are due`() {
        // Given: No PROPOSED event transactions due for settlement
        `when`(trnRepository.findDueEventTransactions(TrnStatus.PROPOSED, EVENT_TYPES, today))
            .thenReturn(emptyList())

        // When: Auto-settle is triggered
        val result = autoSettleService.autoSettleDueTransactions()

        // Then: No transactions should be settled
        assertThat(result).isEqualTo(0)
        verify(trnRepository, never()).save(any(Trn::class.java))
    }

    @Test
    fun `should not settle future dated PROPOSED transactions`() {
        // Given: Only future-dated PROPOSED event transactions exist
        // (These won't be returned by the repository query)
        `when`(trnRepository.findDueEventTransactions(TrnStatus.PROPOSED, EVENT_TYPES, today))
            .thenReturn(emptyList())

        // When: Auto-settle is triggered
        val result = autoSettleService.autoSettleDueTransactions()

        // Then: No transactions should be settled
        assertThat(result).isEqualTo(0)
    }

    private fun createProposedTransaction(
        id: String,
        tradeDate: LocalDate,
        trnType: TrnType = TrnType.DIVI
    ): Trn =
        Trn(
            id = id,
            portfolio = testPortfolio,
            asset = MSFT,
            trnType = trnType,
            tradeDate = tradeDate,
            quantity = BigDecimal("100"),
            price = BigDecimal("0.50"),
            tradeAmount = BigDecimal("50.00"),
            tradeCurrency = USD,
            status = TrnStatus.PROPOSED
        )
}