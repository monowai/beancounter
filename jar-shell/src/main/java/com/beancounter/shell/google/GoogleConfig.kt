package com.beancounter.shell.google

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(SheetIngester::class, GoogleTransport::class, GoogleAuthConfig::class)
open class GoogleConfig
