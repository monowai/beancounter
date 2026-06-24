package com.beancounter.marketdata.assets

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.AccountingType
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.registration.SystemUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The RabbitMQ CSV-import consumer runs on a thread with no JWT in scope and supplies the
 * owner on the AssetInput. Enriching a private asset there must use that owner instead of
 * the security context, or it throws UnauthorizedException → AMQP retries exhausted (DATA-4Z).
 */
@ExtendWith(MockitoExtension::class)
class PrivateMarketEnricherTest {
    @Mock
    private lateinit var systemUserService: SystemUserService

    @Mock
    private lateinit var accountingTypeService: AccountingTypeService

    @Mock
    private lateinit var currencyService: CurrencyService

    @Test
    fun `enriches a private asset from the AssetInput owner without a JWT in scope`() {
        val enricher = PrivateMarketEnricher(systemUserService, accountingTypeService, currencyService)
        val owner = SystemUser(id = "owner-1", email = "owner@test.com")
        // getOrThrow is intentionally NOT stubbed: on the stream thread it has no JWT and
        // would fail. The enricher must resolve the owner via findById instead.
        whenever(systemUserService.findById("owner-1")).thenReturn(owner)
        whenever(currencyService.getCode("SGD")).thenReturn(Currency("SGD"))
        // getOrCreate has two defaulted params; Kotlin dispatches the 4-arg form.
        whenever(accountingTypeService.getOrCreate(any(), any(), any(), any()))
            .thenReturn(mock<AccountingType>())

        val asset =
            enricher.enrich(
                "asset-1",
                Market(PrivateMarketEnricher.ID),
                AssetInput(
                    market = PrivateMarketEnricher.ID,
                    code = "DBS",
                    currency = "SGD",
                    category = "ACCOUNT",
                    owner = "owner-1"
                )
            )

        assertThat(asset.systemUser).isEqualTo(owner)
        assertThat(asset.code).isEqualTo("owner-1.DBS")
    }
}