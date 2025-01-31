package com.beancounter.marketdata.trn

import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.model.Portfolio
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class BcRowAdapterTests {
    @Autowired
    private lateinit var bcRowAdapter: BcRowAdapter

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `sets trade and cash currency to defaults`() {
        val portfolio = Portfolio("1")

        val payload = "20250131,,BUY,US,BRK.B,,,NZD,2025-01-31,0.84060,,,469.9,5,,,-800.52,"
        val trustedTrnImportRequest =
            TrustedTrnImportRequest(
                portfolio,
                ImportFormat.BC,
                "",
                payload.split(",")
            )
        val trnResponse = bcRowAdapter.transform(trustedTrnImportRequest)

        assertThat(trnResponse)
            .hasFieldOrPropertyWithValue("cashCurrency", NZD.code) // Explicitly set
            .hasFieldOrPropertyWithValue("tradeCurrency", USD.code) // Derived from asset market code
    }
}