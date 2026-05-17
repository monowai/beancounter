package com.beancounter.marketdata.assets

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.marketdata.MarketDataBoot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("h2db")
@AutoConfigureMockAuth
class IndexConfigTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var indexConfig: IndexConfig

    @Test
    fun `binds index definitions from yml`() {
        assertThat(indexConfig.values)
            .extracting<String> { it.code }
            .contains("GSPC", "IXIC", "DJI", "FTSE")
    }

    @Test
    fun `each index defines name and currency`() {
        val gspc = indexConfig.values.first { it.code == "GSPC" }
        assertThat(gspc.name).isEqualTo("S&P 500")
        assertThat(gspc.currency).isEqualTo("USD")

        val ftse = indexConfig.values.first { it.code == "FTSE" }
        assertThat(ftse.currency).isEqualTo("GBP")
    }
}