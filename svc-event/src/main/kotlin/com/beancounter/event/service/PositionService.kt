package com.beancounter.event.service

import com.beancounter.auth.TokenService
import com.beancounter.auth.client.LoginService
import com.beancounter.client.AssetService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.common.DateSplitter
import com.beancounter.event.integration.PositionGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import javax.annotation.PostConstruct

/**
 * Locate positions to support nominal corporate events.
 */
@Service
class PositionService(
    private val behaviourFactory: EventBehaviourFactory,
    private val loginService: LoginService
) {
    private val dateUtils = DateUtils()
    private val dateSplitter = DateSplitter(dateUtils)
    private lateinit var assetService: AssetService
    private lateinit var positionGateway: PositionGateway
    private lateinit var portfolioService: PortfolioServiceClient
    private lateinit var tokenService: TokenService

    @Value("\${position.url:http://localhost:9500/api}")
    private lateinit var positionUrl: String

    @Autowired
    fun setAssetService(assetService: AssetService) {
        this.assetService = assetService
    }

    @Autowired
    fun setTokenService(tokenService: TokenService) {
        this.tokenService = tokenService
    }

    @Autowired
    fun setPositionGateway(positionGateway: PositionGateway) {
        this.positionGateway = positionGateway
    }

    @Autowired
    fun setPortfolioClientService(portfolioServiceClient: PortfolioServiceClient) {
        portfolioService = portfolioServiceClient
    }

    @PostConstruct
    fun logConfig() {
        log.info("position.url: {}", positionUrl)
    }

    fun findWhereHeld(assetId: String, date: LocalDate): PortfoliosResponse {
        return portfolioService.getWhereHeld(assetId, date)
    }

    fun process(portfolio: Portfolio, event: CorporateEvent): TrustedTrnEvent? {
        val positionResponse = positionGateway.query(
            tokenService.bearerToken,
            TrustedTrnQuery(portfolio, event.recordDate, event.assetId)
        )
        if (positionResponse != null && positionResponse.data.hasPositions()) {
            val position = positionResponse.data.positions.values.iterator().next()
            // Cash positions do not have Events and Interest is not currently calculated.
            if (includePosition(position)) {
                return behaviourFactory.getAdapter(event)
                    .calculate(positionResponse.data.portfolio, position, event)
            }
        }
        return null // Ignore
    }

    fun includePosition(position: Position): Boolean {
        if (position.asset.assetCategory.isCash()) return false
        return (position.quantityValues.getTotal().compareTo(BigDecimal.ZERO) != 0)
    }

    fun backFillEvents(id: String, date: String = "today") {
        loginService.login()
        val asAt: String = if (date.equals(DateUtils.today, ignoreCase = true)) {
            dateUtils.today()
        } else {
            dateUtils.getDate(date).toString()
        }
        val portfolio = portfolioService.getPortfolioById(id)
        val dates = dateSplitter.split(from = asAt, until = asAt)
        for (thisDate in dates) {
            val positionResponse = positionGateway[tokenService.bearerToken, id, thisDate.toString()]
            if (positionResponse.data.hasPositions()) {
                log.info(
                    "Backfill {} Corporate Actions. id: {}, code: {}, asAt: {}",
                    positionResponse.data.positions.size,
                    portfolio.id,
                    portfolio.code,
                    thisDate
                )
            }
            for (key in positionResponse.data.positions.keys) {
                val position = positionResponse.data.positions[key]
                if (position != null && !position.asset.assetCategory.isCash()) {
                    assetService.backFillEvents(position.asset.id)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PositionService::class.java)
    }
}
