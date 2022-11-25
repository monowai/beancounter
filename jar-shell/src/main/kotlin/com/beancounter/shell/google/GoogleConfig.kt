package com.beancounter.shell.google

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(SheetIngester::class, GoogleGateway::class, GoogleAuthConfig::class)
/**
 * Imports Google Doc related dependencies.
 */
class GoogleConfig
