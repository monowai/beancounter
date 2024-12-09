package com.beancounter.position

import com.beancounter.auth.AutoConfigureMockAuth
import org.junit.jupiter.api.Tag
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

/**
 * Annotate the test to use the MarketData stubs
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ActiveProfiles
@Tag("stubbed")
@SpringBootTest
@AutoConfigureMockAuth
@AutoConfigureMockMvc
@DirtiesContext
annotation class StubbedTest(
    /**
     * Defines the active profiles to be used for the annotated test class.
     */
    val profiles: Array<String> = ["test"]
)