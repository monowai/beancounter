package com.beancounter.marketdata.providers

import org.slf4j.Logger

/**
 * Logs the configured API key status at service startup, masking any non-demo key as
 * "** Redacted **".
 *
 * A key whose value starts with "demo" (case-insensitive) is treated as a placeholder
 * and [demoLabel] is emitted in the log so the environment-variable name and configured
 * mode are both visible.  The [demoLabel] default ("demo") matches AlphaVantage and EODHD;
 * pass "DEMO" for MarketStack to preserve its conventional uppercase label.
 *
 * Log output is byte-for-byte identical to the three inline `@PostConstruct logStatus()`
 * blocks it replaces:
 *   "BEANCOUNTER_MARKET_PROVIDERS_ALPHA_KEY: demo"   / "** Redacted **"
 *   "BEANCOUNTER_MARKET_PROVIDERS_EODHD_KEY: demo"   / "** Redacted **"
 *   "BEANCOUNTER_MARKET_PROVIDERS_MSTACK_KEY: DEMO"  / "** Redacted **"
 */
fun logApiKeyStatus(
    logger: Logger,
    keyName: String,
    apiKey: String,
    demoLabel: String = "demo"
) {
    val display = if (apiKey.startsWith("demo", ignoreCase = true)) demoLabel else "** Redacted **"
    logger.info("{}: {}", keyName, display)
}