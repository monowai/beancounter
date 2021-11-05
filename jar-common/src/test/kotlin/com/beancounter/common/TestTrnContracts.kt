package com.beancounter.common

import com.beancounter.common.Constants.Companion.NYSE
import com.beancounter.common.Constants.Companion.oneString
import com.beancounter.common.TestMarkets.Companion.USD
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
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/**
 * Trn Data tests.
 */
internal class TestTrnContracts {
    private val objectMapper = BcJson().objectMapper

    private val abc = "ABC"

    @Test
    @Throws(Exception::class)
    fun is_TransactionRequestSerializing() {
        val price = BigDecimal("10.99")
        val trnInput = TrnInput(
            callerRef = CallerRef(oneString, oneString, abc),
            tradeDate = DateUtils().getDate("2019-10-10"),
            assetId = getAsset(NYSE, "MSFT").id,
            fees = BigDecimal.ONE,
            price = price,
            tradeAmount = BigDecimal("100.99"),
            comments = "Comment"
        )

        trnInput.cashCurrency = USD.code
        trnInput.cashAsset = toKey("USD-X", "USER")
        trnInput.settleDate = DateUtils().getDate("2019-10-10")
        trnInput.cashAmount = BigDecimal("100.99")
        trnInput.tradeBaseRate = BigDecimal("1.99")
        trnInput.tradePortfolioRate = price
        trnInput.tradeBaseRate = BigDecimal.ONE
        val trnRequest = TrnRequest(abc.lowercase(Locale.getDefault()), arrayOf(trnInput))
        val json = objectMapper.writeValueAsString(trnRequest)
        val fromJson = objectMapper.readValue(json, TrnRequest::class.java)
        assertThat(fromJson)
            .isNotNull
            .isEqualTo(trnRequest)
        assertThat(fromJson.hashCode())
            .isEqualTo(trnRequest.hashCode())
        assertThat(trnRequest == fromJson)
        val fromTrn: TrnInput = fromJson.data.iterator().next()
        assertThat(fromTrn.callerRef).usingRecursiveComparison().isEqualTo(trnInput.callerRef)
        assertThat(fromTrn)
            .usingRecursiveComparison().isEqualTo(trnInput)
    }

    @Test
    @Throws(Exception::class)
    fun is_TransactionResponseSerializing() {
        val trnType = TrnType.BUY
        val asset = getAsset(NYSE, "TEST")
        val portfolio = getPortfolio("TWEE")
        portfolio.owner = SystemUser("123", "whee", true)
        val trn = Trn(trnType, asset, BigDecimal("100.01"))
        trn.id = "PK"
        trn.callerRef = CallerRef(oneString, oneString, "TEST")
        trn.portfolio = portfolio
        trn.tradeDate = LocalDate.now()
        trn.settleDate = LocalDate.now()
        trn.price = BigDecimal("22.11")
        trn.fees = BigDecimal("10")
        trn.tradeAmount = BigDecimal("999.99")
        val trns: MutableCollection<Trn> = ArrayList()
        trns.add(trn)
        val trnResponse = TrnResponse(trns)

        val fromJson = objectMapper.readValue(objectMapper.writeValueAsString(trnResponse), TrnResponse::class.java)
        val fromAsset: Asset = fromJson.data.iterator().next().asset
        // Market.aliases are not serialized
        assertThat(fromAsset.market)
            .usingRecursiveComparison().ignoringFields("market", "aliases")
        assertThat(fromAsset)
            .usingRecursiveComparison()
        assertThat(fromJson.data).hasSize(1)
        assertThat(fromJson.data.iterator().next())
            .usingRecursiveComparison().ignoringFields("asset")
    }

    private val portfolioProp = "portfolio"
    private val importFormatProp = "importFormat"
    private val messageProp = "message"

    @Test
    @Throws(Exception::class)
    fun is_TrustedTrnRequestValid() {
        val row: MutableList<String> = ArrayList()
        row.add(abc)
        row.add(abc)
        val ttr = TrustedTrnImportRequest(getPortfolio("TWEE"), row)
        assertThat(ttr)
            .hasFieldOrPropertyWithValue(importFormatProp, ImportFormat.BC)
            .hasFieldOrPropertyWithValue(messageProp, "")
            .hasFieldOrProperty(portfolioProp)
            .hasFieldOrProperty("callerRef")
            .hasFieldOrProperty("row")

        val json = objectMapper.writeValueAsString(ttr)
        val fromJson = objectMapper.readValue(json, TrustedTrnImportRequest::class.java)
        assertThat(fromJson)
            .usingRecursiveComparison()
            .ignoringFields(portfolioProp)
            .isEqualTo(ttr)
    }

    private val simpleTrnInput = TrnInput(
        assetId = "aid",
        price = BigDecimal.TEN
    )

    @Test
    @Throws(Exception::class)
    fun is_TrustedEventWithMessageValid() {
        val ttr = TrustedTrnEvent(
            portfolio = getPortfolio(),
            message = "the message",
            trnInput = simpleTrnInput
        )

        assertThat(ttr)
            .hasFieldOrPropertyWithValue(importFormatProp, ImportFormat.BC)
            .hasFieldOrProperty(portfolioProp)
            .hasFieldOrPropertyWithValue(messageProp, "the message")
            .hasFieldOrProperty("trnInput")

        val json = objectMapper.writeValueAsString(ttr)
        val fromJson = objectMapper.readValue(json, TrustedTrnEvent::class.java)
        assertThat(fromJson)
            .usingRecursiveComparison()
            .isEqualTo(ttr)
    }

    @Test
    @Throws(Exception::class)
    fun is_TrustedEventRequestValid() {
        val ttr = TrustedTrnEvent(
            portfolio = getPortfolio(),
            trnInput = simpleTrnInput
        )

        assertThat(ttr)
            .hasFieldOrPropertyWithValue(importFormatProp, ImportFormat.BC)
            .hasFieldOrProperty(portfolioProp)
            .hasFieldOrProperty("trnInput")

        compare(ttr)
    }

    private fun compare(ttr: TrustedTrnEvent) {
        val json = objectMapper.writeValueAsString(ttr)
        val fromJson = objectMapper.readValue(json, TrustedTrnEvent::class.java)
        assertThat(fromJson)
            .usingRecursiveComparison()
            .isEqualTo(ttr)
    }

    @Test
    fun is_TrnQuerySerializing() {
        val trustedTrnQuery = TrustedTrnQuery(portfolio = getPortfolio(), assetId = "123")
        val json = objectMapper.writeValueAsString(trustedTrnQuery)
        val fromJson = objectMapper.readValue(json, TrustedTrnQuery::class.java)
        assertThat(fromJson)
            .hasNoNullFieldsOrProperties()
            .usingRecursiveComparison().isEqualTo(trustedTrnQuery)

        assertThat(fromJson.toString()).isNotNull
    }
}
