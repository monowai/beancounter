package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.FxTransactions
import com.beancounter.common.contracts.PositionMoveRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.usdCashBalance
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.portfolio.PortfolioService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * Moving a position writes real SETTLED compensating cash legs. Their trade date has
 * to be "today" in the configured `beancounter.zone` — dating them from the JVM
 * default zone puts a wrong-day entry in the ledger for anyone east of the pod's zone.
 *
 * Kiritimati (UTC+14) is always at least a day ahead of Midway (UTC-11), so the
 * assertion holds whatever the host clock and host zone are.
 */
class PositionMoveZoneTest {
    private val owner = SystemUser("zone-user", "zone@example.com")

    private val sourcePortfolio =
        Portfolio(
            id = "zone-source",
            code = "ZSOURCE",
            currency = USD,
            base = USD,
            owner = owner
        )

    private val targetPortfolio =
        Portfolio(
            id = "zone-target",
            code = "ZTARGET",
            currency = USD,
            base = USD,
            owner = owner
        )

    private val serviceZone = ZoneId.of("Pacific/Kiritimati")

    @Test
    fun `compensating cash legs are dated today in the configured zone`() {
        val jvmDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Midway"))
        try {
            val trnRepository = mock<TrnRepository>()
            val portfolioService = mock<PortfolioService>()
            val trnService = mock<TrnService>()
            val positionMoveService =
                PositionMoveService(
                    trnRepository,
                    portfolioService,
                    mock<FxTransactions>(),
                    trnService,
                    mock<CacheInvalidationProducer>(),
                    DateUtils(serviceZone.id)
                )

            whenever(portfolioService.find(sourcePortfolio.id)).thenReturn(sourcePortfolio)
            whenever(portfolioService.find(targetPortfolio.id)).thenReturn(targetPortfolio)
            whenever(trnRepository.findByPortfolioIdAndAssetId(sourcePortfolio.id, MSFT.id))
                .thenReturn(listOf(buyTrn()))

            positionMoveService.movePosition(
                PositionMoveRequest(
                    sourcePortfolioId = sourcePortfolio.id,
                    targetPortfolioId = targetPortfolio.id,
                    assetId = MSFT.id,
                    maintainCashBalances = true
                )
            )

            val captor = argumentCaptor<TrnRequest>()
            verify(trnService).save(eq(sourcePortfolio), captor.capture())
            verify(trnService).save(eq(targetPortfolio), captor.capture())

            val cashLegDates = captor.allValues.flatMap { it.data }.map { it.tradeDate }
            assertThat(cashLegDates)
                .isNotEmpty
                .allMatch { it == LocalDate.now(serviceZone) }
        } finally {
            TimeZone.setDefault(jvmDefault)
        }
    }

    private fun buyTrn(): Trn =
        Trn(
            id = "zone-trn",
            trnType = TrnType.BUY,
            asset = MSFT,
            quantity = BigDecimal("100"),
            tradeAmount = BigDecimal("5000"),
            tradeCurrency = USD,
            cashAsset = usdCashBalance,
            cashCurrency = USD,
            cashAmount = BigDecimal("-5000"),
            portfolio = sourcePortfolio,
            tradeDate = LocalDate.now(serviceZone).minusDays(10),
            status = TrnStatus.SETTLED
        )
}