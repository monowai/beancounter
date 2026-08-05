package com.beancounter.marketdata.cash

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.Constants.Companion.usdCashBalance
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.trn.TrnService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * `POST /cash/transfer` may omit tradeDate, in which case the transfer is dated
 * "today". That day has to be resolved in the configured `beancounter.zone` — a
 * DTO-level `LocalDate.now()` default resolves in the JVM zone and back-dates the
 * settled legs for any caller east of it (the same defect as the CSV import guard).
 *
 * Kiritimati (UTC+14) is always at least a day ahead of Midway (UTC-11), so the
 * assertion never depends on the wall clock or the host zone.
 */
class CashTransferZoneTest {
    private val serviceZone = ZoneId.of("Pacific/Kiritimati")

    private val portfolio =
        Portfolio(
            id = "zone-transfer",
            code = "ZT",
            currency = USD,
            base = USD,
            owner = SystemUser("zone-user", "zone@example.com")
        )

    @Test
    fun `a transfer with no trade date is dated today in the configured zone`() {
        val jvmDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Midway"))
        try {
            val assetService = mock<AssetService>()
            val portfolioService = mock<PortfolioService>()
            val trnService = mock<TrnService>()

            whenever(assetService.find(usdCashBalance.id)).thenReturn(usdCashBalance)
            whenever(portfolioService.find(portfolio.id)).thenReturn(portfolio)
            whenever(trnService.save(any<Portfolio>(), any<TrnRequest>())).thenReturn(emptyList())

            val cashTransferService =
                CashTransferService(
                    assetService,
                    portfolioService,
                    trnService,
                    DateUtils(serviceZone.id)
                )

            cashTransferService.transfer(
                CashTransferRequest(
                    fromPortfolioId = portfolio.id,
                    fromAssetId = usdCashBalance.id,
                    toPortfolioId = portfolio.id,
                    toAssetId = usdCashBalance.id,
                    sentAmount = BigDecimal("100")
                )
            )

            val captor = argumentCaptor<TrnRequest>()
            verify(trnService, times(2)).save(any<Portfolio>(), captor.capture())

            val legDates = captor.allValues.flatMap { it.data }.map { it.tradeDate }
            assertThat(legDates)
                .isNotEmpty
                .allMatch { it == LocalDate.now(serviceZone) }
        } finally {
            TimeZone.setDefault(jvmDefault)
        }
    }
}