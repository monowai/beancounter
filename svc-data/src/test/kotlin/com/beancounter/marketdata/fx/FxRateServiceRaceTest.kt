package com.beancounter.marketdata.fx

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.FxProviderService
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Regression guard for RETIRE-G / the production `fx_rate_pkey` duplicate-key
 * Sentry event: two concurrent requests for the same uncached date both call the
 * FX provider, both try to write the same deterministic `FxRate.id`
 * (from-to-date-provider).
 *
 * The provider stub's side effect commits the winning insert, in its own
 * REQUIRES_NEW transaction, before returning control to [FxRateService.getRates] -
 * simulating a concurrent winner's request that already landed between this
 * call's cache-miss check and its own attempt to persist the same rates.
 *
 * Empirical note: this specific sequential simulation does NOT reproduce a
 * failure on the pre-fix code (verified by temporarily reverting
 * FxRateService to call `fxRateRepository.saveAll` directly - this test still
 * passed). `FxRate.id` is assigned (never null), so Spring Data's `save()`
 * always routes through `entityManager.merge()`, never `persist()`; merge's
 * existence check does a real `SELECT` before deciding INSERT vs UPDATE, and
 * by the time this call resumes the winner's insert has already committed, so
 * merge finds it and converges to a harmless UPDATE - no exception, with or
 * without [ConflictTolerantWriter]. The real production race needs two
 * genuinely overlapping transactions whose existence-check SELECTs both run
 * before either commits, which a sequential test cannot force. This test is
 * kept as a documentation / non-regression guard for the scenario the packet
 * asked for; the writer's actual conflict-handling contract - including the
 * FxRate assigned-id merge path - is proven directly by
 * `ConflictTolerantWriterTest`, and the equivalent genuinely-reproducible
 * conflict (two brand-new rows racing within one batch) is proven for
 * `MarketData` by `PriceServiceConflictToleranceTest`, which DOES fail on the
 * pre-fix code.
 */
@SpringMvcDbTest
class FxRateServiceRaceTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var fxProviderService: FxProviderService

    @Autowired
    private lateinit var fxRateService: FxRateService

    @Autowired
    private lateinit var fxRateRepository: FxRateRepository

    @Autowired
    private lateinit var currencyService: CurrencyService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    // Historical weekday (matches FxFullStackTest) - isCurrent=false so
    // FxRateService.getDate() resolves straight to this date, no market-close
    // walk-back to reason about.
    private val date = "2019-08-27"

    @Test
    fun `getRates does not fail when a concurrent winner already committed the same rates`() {
        val usd = currencyService.getCode("USD")
        val nzd = currencyService.getCode("NZD")

        whenever(fxProviderService.getDefaultProviderId()).thenReturn("FRANKFURTER")
        whenever(fxProviderService.getRates(eq(date), isNull())).thenAnswer {
            val winnerRates =
                listOf(
                    FxRate(
                        from = usd,
                        to = nzd,
                        rate = BigDecimal("1.6"),
                        date = LocalDate.parse(date),
                        provider = "FRANKFURTER"
                    )
                )
            // The concurrent winner's own request already committed this insert,
            // fully, in its own transaction, before this call returns to
            // FxRateService.getRates - which is still mid cache-miss handling in
            // its own (uncommitted) outer transaction.
            val requiresNew =
                TransactionTemplate(transactionManager).apply {
                    propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
                }
            requiresNew.execute { fxRateRepository.saveAll(winnerRates) }
            winnerRates
        }

        val request =
            FxRequest(
                rateDate = date,
                pairs = mutableSetOf(IsoCurrencyPair("USD", "NZD"))
            )

        assertThatCode { fxRateService.getRates(request, "test-token") }
            .doesNotThrowAnyException()
    }
}