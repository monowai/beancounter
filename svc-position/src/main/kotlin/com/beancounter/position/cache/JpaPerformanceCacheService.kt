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
        log.debug("Cache lookup all: portfolio={}, found={}", portfolioId, entities.size)
        return entities.map { it.toCachedSnapshot() }
    }

    override fun findSnapshots(
        portfolioId: String,
        dates: List<LocalDate>
    ): List<CachedSnapshot> {
        val entities = repository.findByPortfolioIdAndValuationDateIn(portfolioId, dates)
        log.debug("Cache lookup: portfolio={}, requested={}, found={}", portfolioId, dates.size, entities.size)
        return entities.map { it.toCachedSnapshot() }
    }

    @Transactional
    override fun storeSnapshots(
        portfolioId: String,
        snapshots: List<CachedSnapshot>
    ) {
        val datesToStore = snapshots.map { it.valuationDate }
        repository.deleteByPortfolioIdAndValuationDateIn(portfolioId, datesToStore)
        val entities =
            snapshots.map { snapshot ->
                PerformanceSnapshotEntity(
                    id = keyGen.id,
                    portfolioId = portfolioId,
                    valuationDate = snapshot.valuationDate,
                    marketValue = snapshot.marketValue,
                    externalCashFlow = snapshot.externalCashFlow,
                    netContributions = snapshot.netContributions,
                    cumulativeDividends = snapshot.cumulativeDividends
                )
            }
        repository.saveAll(entities)
        log.debug("Cache store: portfolio={}, snapshots={}", portfolioId, snapshots.size)
    }

    @Transactional
    override fun invalidateFrom(
        portfolioId: String,
        fromDate: LocalDate
    ) {
        repository.deleteByPortfolioIdAndValuationDateGreaterThanEqual(portfolioId, fromDate)
        log.debug("Cache invalidate: portfolio={}, from={}", portfolioId, fromDate)
    }

    @Transactional
    override fun invalidateOnDate(date: LocalDate) {
        repository.deleteByValuationDate(date)
        log.debug("Cache invalidate: date={}", date)
    }

    @Transactional
    override fun invalidatePortfolio(portfolioId: String) {
        repository.deleteByPortfolioId(portfolioId)
        log.debug("Cache invalidate: portfolio={}", portfolioId)
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