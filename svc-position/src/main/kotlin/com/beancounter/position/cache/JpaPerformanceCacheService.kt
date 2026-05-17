package com.beancounter.position.cache

import com.beancounter.common.utils.KeyGenUtils
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
@Primary
class JpaPerformanceCacheService(
    private val repository: PerformanceSnapshotRepository
) : PerformanceCacheService {
    private val log = LoggerFactory.getLogger(JpaPerformanceCacheService::class.java)
    private val keyGen = KeyGenUtils()

    override fun findAllSnapshots(portfolioId: String): List<CachedSnapshot> {
        val entities = repository.findByPortfolioIdOrderByValuationDate(portfolioId)
        log.trace("Cache lookup all: portfolio={}, found={}", portfolioId, entities.size)
        return entities.map { it.toCachedSnapshot() }
    }

    override fun findSnapshots(
        portfolioId: String,
        dates: List<LocalDate>
    ): List<CachedSnapshot> {
        val entities = repository.findByPortfolioIdAndValuationDateIn(portfolioId, dates)
        log.trace("Cache lookup: portfolio={}, requested={}, found={}", portfolioId, dates.size, entities.size)
        return entities.map { it.toCachedSnapshot() }
    }

    @Transactional
    override fun storeSnapshots(
        portfolioId: String,
        snapshots: List<CachedSnapshot>
    ) {
        // Application-level upsert: load existing rows for the requested dates,
        // mutate them in place (JPA UPDATE on flush), and insert new entities
        // for missing dates. The previous delete+insert pattern produced
        // duplicate-key violations on uk_portfolio_date when two concurrent
        // GET /{code}/performance requests both missed the cache and tried to
        // write the same row (POSITION-2Q in Sentry).
        val datesToStore = snapshots.map { it.valuationDate }
        val existingByDate =
            repository
                .findByPortfolioIdAndValuationDateIn(portfolioId, datesToStore)
                .associateBy { it.valuationDate }
        val toSave =
            snapshots.map { snapshot ->
                existingByDate[snapshot.valuationDate]?.copy(
                    marketValue = snapshot.marketValue,
                    externalCashFlow = snapshot.externalCashFlow,
                    netContributions = snapshot.netContributions,
                    cumulativeDividends = snapshot.cumulativeDividends
                ) ?: PerformanceSnapshotEntity(
                    id = keyGen.id,
                    portfolioId = portfolioId,
                    valuationDate = snapshot.valuationDate,
                    marketValue = snapshot.marketValue,
                    externalCashFlow = snapshot.externalCashFlow,
                    netContributions = snapshot.netContributions,
                    cumulativeDividends = snapshot.cumulativeDividends
                )
            }
        repository.saveAll(toSave)
        log.trace("Cache store: portfolio={}, snapshots={}", portfolioId, snapshots.size)
    }

    @Transactional
    override fun invalidateFrom(
        portfolioId: String,
        fromDate: LocalDate
    ) {
        repository.deleteByPortfolioIdAndValuationDateGreaterThanEqual(portfolioId, fromDate)
        log.trace("Cache invalidate: portfolio={}, from={}", portfolioId, fromDate)
    }

    @Transactional
    override fun invalidateOnDate(date: LocalDate) {
        repository.deleteByValuationDate(date)
        log.trace("Cache invalidate: date={}", date)
    }

    @Transactional
    override fun invalidateFromDate(fromDate: LocalDate) {
        repository.deleteByValuationDateGreaterThanEqual(fromDate)
        log.trace("Cache invalidate: from={}", fromDate)
    }

    @Transactional
    override fun invalidatePortfolio(portfolioId: String) {
        repository.deleteByPortfolioId(portfolioId)
        log.trace("Cache invalidate: portfolio={}", portfolioId)
    }

    override fun isAvailable(): Boolean = true

    private fun PerformanceSnapshotEntity.toCachedSnapshot() =
        CachedSnapshot(
            valuationDate = valuationDate,
            marketValue = marketValue,
            externalCashFlow = externalCashFlow,
            netContributions = netContributions,
            cumulativeDividends = cumulativeDividends
        )
}