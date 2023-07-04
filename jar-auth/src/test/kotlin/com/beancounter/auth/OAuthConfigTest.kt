package com.beancounter.auth

import com.beancounter.auth.client.ClientPasswordConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cache.CacheManager
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.cloud.openfeign.FeignAutoConfiguration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate

/**
 * Verify code in OauthConfig which is generally mocked.
 */
@SpringBootTest(classes = [ClientPasswordConfig::class, RestTemplate::class])
@ImportAutoConfiguration(
    ClientPasswordConfig::class,
    HttpMessageConvertersAutoConfiguration::class,
    FeignAutoConfiguration::class,
    OAuthConfig::class,
)
@ActiveProfiles("auth")
@AutoConfigureWireMock(port = 0)
class OAuthConfigTest {

    @Autowired
    private lateinit var authConfig: AuthConfig

    @MockBean
    private lateinit var cacheManager: CacheManager

    @MockBean
    private lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun mockJwtService() {
        WiremockAuth.mockOpenConnect(authConfig)
    }

    @Test
    fun authConfig() {
        val decoder = OAuthConfig(cacheManager).jwtDecoder(authConfig)
        assertThat(decoder).isNotNull
    }
}
