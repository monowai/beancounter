package com.beancounter.marketdata.suites

import org.junit.platform.suite.api.IncludeTags
import org.junit.platform.suite.api.SelectPackages
import org.junit.platform.suite.api.Suite

/**
 * Run all tests involving WireMock
 * ./gradlew svc-data:testSuites
 */

@Suite
@IncludeTags("wiremock")
@SelectPackages("com.beancounter.marketdata")
class WiremockSuite

// gradlew svc-data:test --tests com.beancounter.marketdata.suites.WiremockSuite
