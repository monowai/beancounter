package com.beancounter.client.config

import com.beancounter.auth.TokenService
import com.beancounter.client.ingest.AssetIngestService
import com.beancounter.client.ingest.FxTransactions
import com.beancounter.client.services.ActuatorService
import com.beancounter.client.services.ClassificationClient
import com.beancounter.client.services.FxClientService
import com.beancounter.client.services.MarketDataClient
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.RegistrationService
import com.beancounter.client.services.StaticService
import com.beancounter.client.services.TrnService
import com.beancounter.common.utils.DateUtils
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * All client side classes in one handy configuration.
 */
@Import(
    AssetIngestService::class,
    FxClientService::class,
    FxTransactions::class,
    ActuatorService::class,
    DateUtils::class,
    StaticService::class,
    PriceService::class,
    TrnService::class,
    PortfolioServiceClient::class,
    RegistrationService::class,
    ClassificationClient::class,
    MarketDataClient::class,
    TrnService::class,
    TokenService::class,
    RestClientConfig::class
)
@Configuration
class ClientConfig