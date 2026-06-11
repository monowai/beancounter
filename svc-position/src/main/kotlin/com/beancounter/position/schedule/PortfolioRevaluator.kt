package com.beancounter.position.schedule

import com.beancounter.auth.client.LoginService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.valuation.Valuation
import io.sentry.spring.jakarta.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import java.util.concurrent.locks.ReentrantLock

/**
 * Reusable "value every portfolio against today's prices" loop. Called by
 * the daily cron ([PortfolioValuationSchedule]) as a safety net and by the
 * event-driven [PortfolioRevaluationTrigger] ~10 min after a price/FX
 * refresh in svc-data.
 *
 * Holds an internal lock so concurrent triggers (cron + event) don't
 * double-run the loop on the same minute.
 */
@Service
class PortfolioRevaluator(
    private val portfolioServiceClient: PortfolioServiceClient,
    private val valuationService: Valuation
) {
    private var loginService: LoginService? = null
    private val lock = ReentrantLock()

    @Autowired(required = false)
    fun setLoginService(loginService: LoginService?) {
        this.loginService = loginService
    }

    @SentryTransaction(operation = "scheduled", name = "PortfolioRevaluator.revalueAll")
    fun revalueAll(reason: String): Result {
        val currentLoginService = loginService
        if (currentLoginService == null) {
            log.warn("LoginService not available, skipping revaluation ({})", reason)
            return Result.SKIPPED_NO_LOGIN
        }

        // Single-flight: if a run is already in progress, skip rather than queue.
        if (!lock.tryLock()) {
            log.info("Portfolio revaluation already running, skipping trigger ({})", reason)
            return Result.SKIPPED_BUSY
        }
        try {
            return runLocked(currentLoginService, reason)
        } finally {
            lock.unlock()
        }
    }

    private fun runLocked(
        currentLoginService: LoginService,
        reason: String
    ): Result {
        log.info("Portfolio revaluation starting ({})", reason)
        var successCount = 0
        var errorCount = 0
        currentLoginService.retryOnJwtExpiry {
            val token = currentLoginService.loginM2m()
            val bearerToken = "Bearer ${token.token}"
            val allPortfolios = portfolioServiceClient.getAllPortfolios(bearerToken).data
            if (allPortfolios.isEmpty()) {
                log.info("No portfolios in system")
                return@retryOnJwtExpiry
            }
            log.info("Valuing {} portfolios", allPortfolios.size)
            for (portfolio in allPortfolios) {
                try {
                    valuationService.getPositions(
                        portfolio,
                        DateUtils.TODAY,
                        true
                    )
                    successCount++
                    log.debug("Valued portfolio: {} ({})", portfolio.code, portfolio.id)
                } catch (e: RestClientException) {
                    errorCount++
                    log.error("Failed to value portfolio: {} ({})", portfolio.code, portfolio.id, e)
                } catch (e: BusinessException) {
                    errorCount++
                    log.error("Failed to value portfolio: {} ({})", portfolio.code, portfolio.id, e)
                }
            }
        }
        log.info(
            "Portfolio revaluation completed ({}): {} success, {} errors",
            reason,
            successCount,
            errorCount
        )
        return Result.completed(successCount, errorCount)
    }

    enum class ResultStatus { COMPLETED, SKIPPED_BUSY, SKIPPED_NO_LOGIN }

    data class Result(
        val status: ResultStatus,
        val successCount: Int = 0,
        val errorCount: Int = 0
    ) {
        companion object {
            val SKIPPED_BUSY = Result(ResultStatus.SKIPPED_BUSY)
            val SKIPPED_NO_LOGIN = Result(ResultStatus.SKIPPED_NO_LOGIN)

            fun completed(
                successCount: Int,
                errorCount: Int
            ): Result = Result(ResultStatus.COMPLETED, successCount, errorCount)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PortfolioRevaluator::class.java)
    }
}