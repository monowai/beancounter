package com.beancounter.position.schedule

import com.beancounter.auth.client.LoginService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.valuation.Valuation
import io.sentry.spring.jakarta.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Scheduled task to value portfolios that haven't been valued recently.
 * This keeps the portfolio marketValue updated with current prices.
 *
 * Runs on schedule (default: every 10 min, 5-7 AM Tue-Sat) and values
 * portfolios that haven't been valued in the last 24 hours.
 *
 * For each portfolio valued:
 * 1. Fetches latest prices for all assets
 * 2. Calculates positions and market values
 * 3. Sends updated marketValue to svc-data via Kafka/Stream
 */
@Service
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class PortfolioValuationSchedule(
    private val portfolioServiceClient: PortfolioServiceClient,
    private val valuationService: Valuation,
    private val dateUtils: DateUtils,
    @Value("\${valuation.schedule:0 0/10 5-7 * * Tue-Sat}") private val schedule: String,
    @Value("\${valuation.stale.hours:24}") private val staleHours: Long = 24
) {
    private var loginService: LoginService? = null
    private val log = LoggerFactory.getLogger(PortfolioValuationSchedule::class.java)

    @Autowired(required = false)
    fun setLoginService(loginService: LoginService?) {
        this.loginService = loginService
    }

    init {
        log.info(
            "PortfolioValuationSchedule initialized with cron: {}, zone: {}",
            schedule,
            dateUtils.zoneId.id
        )
    }

    @SentryTransaction(operation = "scheduled", name = "PortfolioValuationSchedule.valueStalePortfolios")
    @Scheduled(
        cron = "\${valuation.schedule:0 0/10 5-7 * * Tue-Sat}",
        zone = "#{@scheduleZone}"
    )
    fun valueStalePortfolios() {
        val currentLoginService = loginService
        if (currentLoginService == null) {
            log.warn("LoginService not available, skipping portfolio valuation")
            return
        }

        log.info(
            "Portfolio valuation scheduled task starting {} - {}",
            LocalDateTime.now(dateUtils.zoneId),
            dateUtils.zoneId.id
        )

        currentLoginService.retryOnJwtExpiry {
            // Authenticate with M2M credentials
            val token = currentLoginService.loginM2m()
            val bearerToken = "Bearer ${token.token}"

            // Get all portfolios in the system
            val allPortfolios = portfolioServiceClient.getAllPortfolios(bearerToken).data

            if (allPortfolios.isEmpty()) {
                log.info("No portfolios in system")
                return@retryOnJwtExpiry
            }

            // Filter to only portfolios that need valuation (not valued in last 24 hours)
            val stalePortfolios = allPortfolios.filter { needsValuation(it) }

            if (stalePortfolios.isEmpty()) {
                log.info("All {} portfolios are up to date", allPortfolios.size)
                return@retryOnJwtExpiry
            }

            log.info(
                "Found {} stale portfolios out of {} total",
                stalePortfolios.size,
                allPortfolios.size
            )

            var successCount = 0
            var errorCount = 0

            for (portfolio in stalePortfolios) {
                try {
                    valuationService.getPositions(
                        portfolio,
                        DateUtils.TODAY,
                        true
                    )
                    successCount++
                    log.debug(
                        "Valued portfolio: {} ({}) - last valued: {}",
                        portfolio.code,
                        portfolio.id,
                        portfolio.valuedAt
                    )
                } catch (e: Exception) {
                    errorCount++
                    log.error(
                        "Failed to value portfolio: {} ({}): {}",
                        portfolio.code,
                        portfolio.id,
                        e.message
                    )
                }
            }

            log.info(
                "Portfolio valuation completed: {} success, {} errors",
                successCount,
                errorCount
            )
        }
    }

    /**
     * Determines if a portfolio needs valuation based on when it was last valued.
     * A portfolio needs valuation if:
     * - It has never been valued (valuedAt is null)
     * - It was valued more than [staleHours] ago (default 24 hours)
     */
    private fun needsValuation(portfolio: Portfolio): Boolean {
        val valuedAt = portfolio.valuedAt ?: return true
        val staleDate = LocalDate.now(dateUtils.zoneId).minusDays(staleHours / 24)
        return valuedAt.isBefore(staleDate)
    }
}