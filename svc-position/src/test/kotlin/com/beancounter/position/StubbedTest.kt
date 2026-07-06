package com.beancounter.position

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.position.config.TestClassificationConfig
import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

/**
 * Annotate the test to use the MarketData stubs
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.CLASSPATH,
    ids = ["beancounter:svc-data:0.1.1:stubs:10993"]
)
// Profiles are hard-wired here: a custom `profiles` attribute aliased with
// @AliasFor does not survive Kotlin annotation compilation reliably (see
// SpringMvcDbTest), and no caller overrode it. These profiles point the
// marketdata RestClient at the stub-runner port (10993).
@ActiveProfiles("svc-position-shared", "contract-base")
@Tag("stubbed")
@SpringBootTest
@AutoConfigureMockAuth
@AutoConfigureMockMvc
@Import(TestClassificationConfig::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
annotation class StubbedTest