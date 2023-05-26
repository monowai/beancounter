package com.beancounter.event.integ

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.event.service.EventService
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties

/**
 * To be implemented.
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = [
        "org.beancounter:svc-data:+:stubs:11999",
        "org.beancounter:svc-position:+:stubs:12999",
    ],
)
@Tag("slow")
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMockAuth
class EventServiceTests {
    @Autowired
    lateinit var eventService: EventService
}
