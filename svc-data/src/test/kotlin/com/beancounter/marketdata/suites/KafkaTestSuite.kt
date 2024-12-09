package com.beancounter.marketdata.suites

import org.junit.platform.suite.api.IncludeTags
import org.junit.platform.suite.api.SelectPackages
import org.junit.platform.suite.api.Suite

/**
 * Run all tests that use KAFKA.
 *
 * ./gradlew svc-data:testSuites --tests com.beancounter.marketdata.suites.KafkaTestSuite
 */

@Suite
@IncludeTags("kafka")
@SelectPackages("com.beancounter.marketdata.broker")
class KafkaTestSuite