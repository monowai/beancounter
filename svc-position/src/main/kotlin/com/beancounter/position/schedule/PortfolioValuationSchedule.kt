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
 * Scheduled task to value all portfolios with current market prices.
 *
 * Runs on schedule (default: every 10 min, 5-7 AM Tue-Sat) and values
 * all portfolios to ensure they have up-to-date market valuations.
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

    @SentryTransaction(operation = "scheduled", name = "PortfolioValuationSchedule.valuePortfolios")
    @Scheduled(
        cron = "\${valuation.schedule:0 0/10 5-7 * * Tue-Sat}",
        zone = "#{@scheduleZone}"
    )
    fun valuePortfolios() {
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

            log.info("Valuing {} portfolios", allPortfolios.size)

            var successCount = 0
            var errorCount = 0

            for (portfolio in allPortfolios) {
                try {
                    valuationService.getPositions(
                        portfolio,
                        DateUtils.TODAY,
                        true
                    )
                    successCount++
                    log.debug(
                        "Valued portfolio: {} ({})",
                        portfolio.code,
                        portfolio.id
                    )
                } catch (e: RuntimeException) {
                    errorCount++
                    log.error(
                        "Failed to value portfolio: {} ({})",
                        portfolio.code,
                        portfolio.id,
                        e
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