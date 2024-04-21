package com.beancounter.marketdata.suites

import org.junit.platform.suite.api.IncludeTags
import org.junit.platform.suite.api.SelectPackages
import org.junit.platform.suite.api.Suite

/**
 * Run all tests involving DB
 * ./gradlew svc-data:testSuites
 */
@Suite
@IncludeTags("db")
@SelectPackages("com.beancounter.marketdata")
class DbTestSuite
