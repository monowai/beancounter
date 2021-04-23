package com.beancounter.shell

import com.beancounter.shell.google.GoogleAuthConfig
import com.beancounter.shell.google.GoogleGateway
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [GoogleGateway::class, GoogleAuthConfig::class])
/**
 * Not mocking Google, so this is just a simple wiring test.
 */
class TestGoogleSheet {
    @Autowired
    private lateinit var googleGateway: GoogleGateway

    @Test
    fun is_TransportInitialised() {
        Assertions.assertThat(googleGateway.httpTransport).isNotNull
    }
}
