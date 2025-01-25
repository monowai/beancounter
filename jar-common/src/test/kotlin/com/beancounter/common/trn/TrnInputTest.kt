package com.beancounter.common.trn

import com.beancounter.common.Constants
import com.beancounter.common.TestMarkets
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.ImportFormat
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.common.input.TrustedTrnImportRequest
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Asset
import com.beancounter.common.model.CallerRef
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.AssetKeyUtils
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/**
 * Trn Data tests.
 */
internal class TrnInputTest {
    private val abc = "ABC"

    @Test
    fun is_TransactionRequestSerializing() {
        val price = BigDecimal("10.99")
        val trnInput =
            TrnInput(
                callerRef =
                    CallerRef(
                        Constants.ONE,
                        Constants.ONE,
                        abc
                    ),
                tradeDate = DateUtils().getFormattedDate("2019-10-10"),
                settleDate = DateUtils().getFormattedDate("2019-10-10"),
                assetId =
                    AssetUtils
                        .getTestAsset(
                            Constants.NYSE,
                            "MSFT"
                        ).id,
                cashAssetId =
                    AssetKeyUtils.toKey(
                        "USD-X",
                        "USER"
                    ),
                cashCurrency = TestMarkets.USD.code,
                fees = BigDecimal.ONE,
                price = price,
                tradeAmount = BigDecimal("100.99"),
                cashAmount = BigDecimal("100.99"),
                tradeBaseRate = BigDecimal.ONE,
                tradePortfolioRate = price,
                comments = "Comment"
            )

        val trnRequest =
            TrnRequest(
                abc.lowercase(Locale.getDefault()),
                arrayOf(trnInput)
            )
        val json = BcJson.objectMapper.writeValueAsString(trnRequest)
        val fromJson =
            BcJson.objectMapper.readValue(
                json,
                TrnRequest::class.java
            )
        assertThat(fromJson)
            .isNotNull
            .isEqualTo(trnRequest)
        assertThat(fromJson.hashCode())
            .isEqualTo(trnRequest.hashCode())
        assertThat(trnRequest == fromJson)
        val fromTrn: TrnInput = fromJson.data.iterator().next()
        assertThat(fromTrn.callerRef).usingRecursiveComparison().isEqualTo(trnInput.callerRef)
        assertThat(fromTrn)
            .usingRecursiveComparison()
            .isEqualTo(trnInput)
    }

    @Test
    fun is_TransactionResponseSerializing() {
        val trnType = TrnType.BUY
        val asset =
            AssetUtils.getTestAsset(
                Constants.NYSE,
                "TEST"
            )
        val portfolio = PortfolioUtils.getPortfolio("TWEE")
        portfolio.owner =
            SystemUser(
                "123",
                "whee",
                true
            )
        val trn =
            Trn(
                id = "PK",
                trnType = trnType,
                tradeDate = LocalDate.now(),
                asset = asset,
                callerRef =
                    CallerRef(
                        Constants.ONE,
                        Constants.ONE,
                        "TEST"
                    ),
                price = BigDecimal("100.01"),
                portfolio = portfolio
            )
        trn.settleDate = LocalDate.now()
        trn.price = BigDecimal("22.11")
        trn.fees = BigDecimal("10")
        trn.tradeAmount = BigDecimal("999.99")
        val trns: MutableCollection<Trn> = ArrayList()
        trns.add(trn)
        val trnResponse = TrnResponse(trns)

        val fromJson =
            BcJson.objectMapper.readValue(
                BcJson.objectMapper.writeValueAsString(trnResponse),
                TrnResponse::class.java
            )
        val fromAsset: Asset =
            fromJson.data
                .iterator()
                .next()
                .asset
        // Market.aliases are not serialized
        assertThat(fromAsset.market)
            .usingRecursiveComparison()
            .ignoringFields(
                "market",
                "aliases"
            )
        assertThat(fromAsset)
            .usingRecursiveComparison()
        assertThat(fromJson.data).hasSize(1)
        assertThat(fromJson.data.iterator().next())
            .usingRecursiveComparison()
            .ignoringFields("asset")
    }

    private val portfolioProp = "portfolio"
    private val importFormatProp = "importFormat"
    private val messageProp = "message"

    @Test
    fun is_TrustedTrnRequestValid() {
        val row: List<String> =
            listOf(
                abc,
                abc,
                abc
            )
        val ttr =
            TrustedTrnImportRequest(
                PortfolioUtils.getPortfolio("TWEE"),
                row = row
            )
        assertThat(ttr)
            .hasFieldOrPropertyWithValue(
                importFormatProp,
                ImportFormat.BC
            ).hasFieldOrPropertyWithValue(
                messageProp,
                ""
            ).hasFieldOrProperty(portfolioProp)
            .hasFieldOrProperty("callerRef")
            .hasFieldOrProperty("row")

        val json = BcJson.objectMapper.writeValueAsString(ttr)
        val fromJson =
            BcJson.objectMapper.readValue(
                json,
                TrustedTrnImportRequest::class.java
            )
        assertThat(fromJson)
            .usingRecursiveComparison()
            .ignoringFields(portfolioProp)
            .isEqualTo(ttr)
    }

    private val simpleTrnInput =
        TrnInput(
            assetId = "aid",
            price = BigDecimal.TEN
        )

    @Test
    fun is_TrustedEventWithMessageValid() {
        val ttr =
            TrustedTrnEvent(
                portfolio = PortfolioUtils.getPortfolio(),
                message = "the message",
                trnInput = simpleTrnInput
            )

        assertThat(ttr)
            .hasFieldOrPropertyWithValue(
                importFormatProp,
                ImportFormat.BC
            ).hasFieldOrProperty(portfolioProp)
            .hasFieldOrPropertyWithValue(
                messageProp,
                "the message"
            ).hasFieldOrProperty("trnInput")

        val json = BcJson.objectMapper.writeValueAsString(ttr)
        val fromJson =
            BcJson.objectMapper.readValue(
                json,
                TrustedTrnEvent::class.java
            )
        assertThat(fromJson)
            .usingRecursiveComparison()
            .isEqualTo(ttr)
    }

    @Test
    @Throws(Exception::class)
    fun is_TrustedEventRequestValid() {
        val ttr =
            TrustedTrnEvent(
                portfolio = PortfolioUtils.getPortfolio(),
                trnInput = simpleTrnInput
            )

        assertThat(ttr)
            .hasFieldOrPropertyWithValue(
                importFormatProp,
                ImportFormat.BC
            ).hasFieldOrProperty(portfolioProp)
            .hasFieldOrProperty("trnInput")

        compare(ttr)
    }

    private fun compare(ttr: TrustedTrnEvent) {
        val json = BcJson.objectMapper.writeValueAsString(ttr)
        val fromJson =
            BcJson.objectMapper.readValue(
                json,
                TrustedTrnEvent::class.java
            )
        assertThat(fromJson)
            .usingRecursiveComparison()
            .isEqualTo(ttr)
    }

    @Test
    fun is_TrnQuerySerializing() {
        val trustedTrnQuery =
            TrustedTrnQuery(
                portfolio = PortfolioUtils.getPortfolio(),
                assetId = "123"
            )
        val json = BcJson.objectMapper.writeValueAsString(trustedTrnQuery)
        val fromJson =
            BcJson.objectMapper.readValue(
                json,
                TrustedTrnQuery::class.java
            )
        assertThat(fromJson)
            .hasNoNullFieldsOrProperties()
            .usingRecursiveComparison()
            .isEqualTo(trustedTrnQuery)

        assertThat(fromJson.toString()).isNotNull
    }
}