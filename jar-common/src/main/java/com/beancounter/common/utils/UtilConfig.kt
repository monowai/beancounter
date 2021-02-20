package com.beancounter.common.utils

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(DateUtils::class)
class UtilConfig
