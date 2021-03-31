package com.beancounter.common.utils

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(DateUtils::class, BcJson::class, NumberUtils::class, PercentUtils::class)
/**
 * Configuration support class to support wiring up common utility services.
 */
class UtilConfig
