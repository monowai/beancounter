package com.beancounter.marketdata.persistence

import com.beancounter.common.model.Asset
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.MarketData
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.assets.AssetRepository
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.FxRateRepository
import com.beancounter.marketdata.providers.MarketDataRepo
import jakarta.persistence.PersistenceException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException
import java.time.LocalDate

/**
 * Reproduces the two production Sentry events (duplicate `fx_rate` PK, duplicate
 * `MarketData(source, asset_id, priceDate)` unique key) at the writer level: a
 * concurrent writer's row is already committed by the time the batch flush runs,
 * so the batch (or a single row, for the assigned-id merge path) hits a
 * unique-constraint conflict instead of the caller's own check-then-insert logic
 * ever seeing it coming.
 */
@SpringMvcDbTest
class ConflictTolerantWriterTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var writer: ConflictTolerantWriter

    @Autowired
    private lateinit var marketDataRepo: MarketDataRepo

    @Autowired
    private lateinit var assetRepository: AssetRepository

    @Autowired
    private lateinit var fxRateRepository: FxRateRepository

    @Autowired
    private lateinit var currencyService: CurrencyService

    @Test
    fun `should write every row when nothing conflicts`() {
        val asset =
            assetRepository.save(
                Asset(
                    code = "CTW-OK",
                    market = NASDAQ,
                    marketCode = NASDAQ.code
                )
            )
        val rows =
            (1..3).map { day ->
                MarketData(
                    asset = asset,
                    priceDate = LocalDate.of(2026, 1, day),
                    close = BigDecimal(100 + day)
                )
            }

        val written = writer.saveAll(marketDataRepo, rows)

        assertThat(written).hasSize(3)
        assertThat(
            marketDataRepo.findByAssetIdAndPriceDateBetween(
                asset.id,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 3)
            )
        ).hasSize(3)
    }

    @Test
    fun `should skip only the conflicting row and keep the rest when a batch hits a duplicate key`() {
        val asset =
            assetRepository.save(
                Asset(
                    code = "CTW-DUP",
                    market = NASDAQ,
                    marketCode = NASDAQ.code
                )
            )
        val date = LocalDate.of(2026, 2, 1)
        // Committed ahead of time by a "concurrent winner" - marketDataRepo.save()
        // goes through persist() (MarketData.id is a fresh generated UUID), a real
        // INSERT that commits immediately under @SpringMvcDbTest's per-test-method
        // transaction-less repo calls.
        marketDataRepo.save(
            MarketData(
                asset = asset,
                priceDate = date,
                close = BigDecimal("10.00")
            )
        )

        // Same natural key (source, asset_id, priceDate) as the row above but a
        // distinct generated id - exactly what a second, unrelated in-memory
        // MarketData instance for the same slot looks like. buildCreateSet's
        // dedup read cannot see this from another transaction; only the unique
        // constraint at flush time catches it.
        val duplicateOfExisting =
            MarketData(
                asset = asset,
                priceDate = date,
                close = BigDecimal("11.00")
            )
        val freshRow =
            MarketData(
                asset = asset,
                priceDate = date.plusDays(1),
                close = BigDecimal("12.00")
            )

        val written = writer.saveAll(marketDataRepo, listOf(duplicateOfExisting, freshRow))

        assertThat(written).hasSize(1)
        assertThat(written.first().priceDate).isEqualTo(freshRow.priceDate)

        val stored =
            marketDataRepo.findByAssetIdAndPriceDateBetween(
                asset.id,
                date,
                date.plusDays(1)
            )
        assertThat(stored).hasSize(2)

        // Proves the failed flush never poisoned this test's persistence context -
        // a Hibernate session left dirty after a failed flush throws on any further use.
        assertThatCode {
            marketDataRepo.findByAssetIdAndPriceDate(asset.id, date)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `should update in place via merge when an FxRate with the same deterministic id already exists`() {
        val usd = currencyService.getCode("USD")
        val aud = currencyService.getCode("AUD")
        val date = LocalDate.of(2026, 3, 1)
        val existing =
            fxRateRepository.save(
                FxRate(
                    from = usd,
                    to = aud,
                    rate = BigDecimal("1.50"),
                    date = date,
                    provider = "FRANKFURTER"
                )
            )

        // FxRate.id is assigned, not generated (from-to-date-provider), and is never
        // null - so Spring Data's save() always routes through entityManager.merge(),
        // never persist(). Exercise that path explicitly: a second in-memory FxRate
        // with the identical id converges to an UPDATE rather than an INSERT conflict.
        val sameIdDifferentRate =
            FxRate(
                from = usd,
                to = aud,
                rate = BigDecimal("1.55"),
                date = date,
                provider = "FRANKFURTER"
            )
        assertThat(sameIdDifferentRate.id).isEqualTo(existing.id)

        val written = writer.saveAll(fxRateRepository, listOf(sameIdDifferentRate))

        assertThat(written).hasSize(1)
        val stored = fxRateRepository.findById(existing.id)
        assertThat(stored).isPresent
        assertThat(stored.get().rate).isEqualByComparingTo(BigDecimal("1.55"))
    }

    @Test
    fun `should propagate non-constraint failures`() {
        val repo = mock<JpaRepository<MarketData, String>>()
        whenever(repo.saveAllAndFlush(any<List<MarketData>>()))
            .thenThrow(DataAccessResourceFailureException("connection pool exhausted"))
        val row =
            MarketData(
                asset =
                    Asset(
                        code = "CTW-FAIL",
                        market = NASDAQ,
                        marketCode = NASDAQ.code
                    ),
                priceDate = LocalDate.of(2026, 4, 1),
                close = BigDecimal.TEN
            )

        assertThatThrownBy { writer.saveAll(repo, listOf(row)) }
            .isInstanceOf(DataAccessResourceFailureException::class.java)
            .hasMessageContaining("connection pool exhausted")
    }

    @Test
    fun `should propagate a NOT NULL violation instead of dropping the row`() {
        val repo = mock<JpaRepository<MarketData, String>>()
        whenever(repo.saveAllAndFlush(any<List<MarketData>>()))
            .thenThrow(
                DataIntegrityViolationException(
                    "not null",
                    ConstraintViolationException(
                        "not null",
                        SQLException("not null", "23502"),
                        "insert ...",
                        ConstraintViolationException.ConstraintKind.NOT_NULL,
                        "close"
                    )
                )
            )
        val row =
            MarketData(
                asset =
                    Asset(
                        code = "CTW-NN",
                        market = NASDAQ,
                        marketCode = NASDAQ.code
                    ),
                priceDate = LocalDate.of(2026, 5, 1),
                close = BigDecimal.TEN
            )

        assertThatThrownBy { writer.saveAll(repo, listOf(row)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)

        verify(
            repo,
            never()
        ).saveAndFlush(any())
    }

    @Test
    fun `should propagate a FOREIGN KEY violation instead of dropping the row`() {
        val repo = mock<JpaRepository<MarketData, String>>()
        whenever(repo.saveAllAndFlush(any<List<MarketData>>()))
            .thenThrow(
                DataIntegrityViolationException(
                    "foreign key",
                    ConstraintViolationException(
                        "foreign key",
                        SQLException("foreign key", "23503"),
                        "insert ...",
                        ConstraintViolationException.ConstraintKind.FOREIGN_KEY,
                        "fk_market_data_asset"
                    )
                )
            )
        val row =
            MarketData(
                asset =
                    Asset(
                        code = "CTW-FK",
                        market = NASDAQ,
                        marketCode = NASDAQ.code
                    ),
                priceDate = LocalDate.of(2026, 5, 2),
                close = BigDecimal.TEN
            )

        assertThatThrownBy { writer.saveAll(repo, listOf(row)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)

        verify(
            repo,
            never()
        ).saveAndFlush(any())
    }

    @Test
    fun `should retry when a unique violation surfaces without Hibernate's wrapper`() {
        val repo = mock<JpaRepository<MarketData, String>>()
        val row =
            MarketData(
                asset =
                    Asset(
                        code = "CTW-BARE",
                        market = NASDAQ,
                        marketCode = NASDAQ.code
                    ),
                priceDate = LocalDate.of(2026, 5, 3),
                close = BigDecimal.TEN
            )
        whenever(repo.saveAllAndFlush(any<List<MarketData>>()))
            .thenThrow(
                PersistenceException(
                    SQLIntegrityConstraintViolationException("dup", "23505")
                )
            )
        whenever(repo.saveAndFlush(any<MarketData>())).thenReturn(row)

        val written = writer.saveAll(repo, listOf(row))

        assertThat(written).containsExactly(row)
        verify(
            repo,
            times(1)
        ).saveAndFlush(row)
    }
}