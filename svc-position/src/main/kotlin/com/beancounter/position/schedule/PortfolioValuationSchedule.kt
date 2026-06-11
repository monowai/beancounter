package com.beancounter.position.schedule

import com.beancounter.auth.client.LoginService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.valuation.Valuation
import io.sentry.spring.jakarta.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import java.time.LocalDateTime

/**
 * Scheduled task to value all portfolios with current market prices.
 *
 * Runs on schedule (default: 18:30 SGT Tue-Sat — after bc-data's
 * 07:00-18:00 SGT price-refresh window) and values all portfolios so
 * they have up-to-date market valuations. Firing earlier (e.g. pre
 * Asia open) leaves gainOnDay=0 for Asia/Pacific positions because
 * previousClose isn't in market_data yet.
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
    @Value("\${valuation.schedule:0 30 18 * * Tue-Sat}") private val schedule: String
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
        cron = "\${valuation.schedule:0 30 18 * * Tue-Sat}",
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
                } catch (e: RestClientException) {
                    errorCount++
                    log.error(
                        "Failed to value portfolio: {} ({})",
                        portfolio.code,
                        portfolio.id,
                        e
                    )
                } catch (e: BusinessException) {
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