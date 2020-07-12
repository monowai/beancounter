package com.beancounter.event.service

import com.beancounter.auth.common.TokenService
import com.beancounter.client.AssetService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.integration.PositionGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import javax.annotation.PostConstruct

@Service
class PositionService(private val behaviourFactory: EventBehaviourFactory) {
    private val dateUtils = DateUtils()
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

    fun findWhereHeld(assetId: String, date: LocalDate?): PortfoliosResponse {
        return portfolioService.getWhereHeld(assetId, date)
    }

    fun process(portfolio: Portfolio, event: CorporateEvent): TrustedTrnEvent? {
        val positionResponse = positionGateway.query(
                tokenService.bearerToken,
                TrustedTrnQuery(portfolio, event.recordDate, event.assetId))
        if (positionResponse != null) {
            if (positionResponse.data.hasPositions()) {
                val position = positionResponse.data.positions.values.iterator().next()
                if (position.quantityValues.getTotal().compareTo(BigDecimal.ZERO) != 0) {
                    val behaviour = behaviourFactory.getAdapter(event)!!
                    return behaviour
                            .calculate(positionResponse.data.portfolio, position, event)
                }
            }
        }
        return null
    }

    fun backFillEvents(code: String, date: String?) {
        val (_, code1) = portfolioService.getPortfolioByCode(code)
        val asAt: String? = if (date == null || date.equals("today", ignoreCase = true)) {
            dateUtils.today()
        } else {
            dateUtils.getDateString(dateUtils.getDate(date))
        }
        val results = positionGateway.get(
                        tokenService.bearerToken,
                        code1,
                        asAt)
        for (key in results!!.data.positions.keys) {
            val position = results.data.positions[key]
            if (position!!.quantityValues.getTotal().compareTo(BigDecimal.ZERO) != 0) {
                assetService.backFillEvents(position.asset.id!!)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PositionService::class.java)
    }

}