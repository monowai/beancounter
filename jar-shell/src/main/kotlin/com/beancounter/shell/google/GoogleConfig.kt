package com.beancounter.shell.google

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Imports Google Doc related dependencies.
 */
@Configuration
@Import(SheetIngester::class, GoogleGateway::class, GoogleAuthConfig::class)
class GoogleConfig
