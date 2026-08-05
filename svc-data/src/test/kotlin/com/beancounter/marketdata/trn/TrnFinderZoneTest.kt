package com.beancounter.marketdata.trn

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.usdCashBalance
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.portfolio.PortfolioShareRepository
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * The cash ladder is bounded by "today". Which day that is has to come from the
 * configured `beancounter.zone`, not the JVM default zone — the pods run UTC while
 * the users are SGT, so a bare `LocalDate.now()` cutoff drops trades the user made
 * today until UTC catches up.
 *
 * Kiritimati (UTC+14) is always at least a day ahead of Midway (UTC-11), so these
 * assertions do not depend on the wall clock or on the host's zone.
 */
@ExtendWith(MockitoExtension::class)
class TrnFinderZoneTest {
    @Mock
    private lateinit var trnRepository: TrnRepository

    @Mock
    private lateinit var portfolioService: PortfolioService

    @Mock
    private lateinit var systemUserService: SystemUserService

    @Mock
    private lateinit var portfolioShareRepository: PortfolioShareRepository

    @Mock
    private lateinit var trnPostProcessor: TrnPostProcessor

    private val serviceZone = ZoneId.of("Pacific/Kiritimati")

    @Test
    fun `cash ladder includes a trade dated today in the configured zone`() {
        val jvmDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Midway"))
        try {
            val portfolio = Portfolio("ladder")
            val today = LocalDate.now(serviceZone)
            val todaysTrade =
                Trn(
                    trnType = TrnType.DEPOSIT,
                    tradeDate = today,
                    asset = usdCashBalance,
                    cashAsset = usdCashBalance,
                    portfolio = portfolio
                )

            whenever(portfolioService.find(portfolio.id)).thenReturn(portfolio)
            // Stand in for the repository's `tradeDate <= asAt` bound so the cutoff
            // the finder chooses decides what comes back.
            whenever(
                trnRepository.findByPortfolioIdAndCashAssetId(
                    eq(portfolio.id),
                    eq(usdCashBalance.id),
                    any(),
                    eq(TrnStatus.SETTLED)
                )
            ).thenAnswer { invocation ->
                val asAt = invocation.getArgument<LocalDate>(2)
                if (todaysTrade.tradeDate.isAfter(asAt)) emptyList() else listOf(todaysTrade)
            }
            whenever(trnPostProcessor.postProcess(any<List<Trn>>()))
                .thenAnswer { it.getArgument<List<Trn>>(0) }

            val trnFinder =
                TrnFinder(
                    trnRepository,
                    portfolioService,
                    systemUserService,
                    portfolioShareRepository,
                    DateUtils(serviceZone.id),
                    trnPostProcessor
                )

            assertThat(trnFinder.getCashLadder(portfolio.id, usdCashBalance.id))
                .containsExactly(todaysTrade)
        } finally {
            TimeZone.setDefault(jvmDefault)
        }
    }
}