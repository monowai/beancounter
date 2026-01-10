package com.beancounter.position.schedule

import com.beancounter.auth.client.LoginService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.valuation.Valuation
import io.sentry.spring.jakarta.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Scheduled task to value all portfolios in the system daily.
 * This keeps the portfolio marketValue updated with current prices.
 *
 * Runs daily (default: 7:00 AM) to value each portfolio, which:
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
    @Value("\${valuation.schedule:0 0/10 5-7 * * Tue-Sat}") private val schedule: String
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

    @SentryTransaction(operation = "scheduled", name = "PortfolioValuationSchedule.valueAllPortfolios")
    @Scheduled(
        cron = "\${valuation.schedule:0 0/10 5-7 * * Tue-Sat}",
        zone = "#{@scheduleZone}"
    )
    fun valueAllPortfolios() {
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
            val portfolios = portfolioServiceClient.getAllPortfolios(bearerToken).data

            if (portfolios.isEmpty()) {
                log.info("No portfolios to value")
                return@retryOnJwtExpiry
            }

            log.info("Starting valuation for {} portfolios", portfolios.size)
            var successCount = 0
            var errorCount = 0

            for (portfolio in portfolios) {
                try {
                    valuationService.getPositions(
                        portfolio,
                        DateUtils.TODAY,
                        true
                    )
                    successCount++
                    log.debug("Valued portfolio: {} ({})", portfolio.code, portfolio.id)
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
}