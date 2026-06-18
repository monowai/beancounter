package com.beancounter.common.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Wire-format representation of a Trn. Carries id references for Asset,
 * Portfolio, Currency and Broker so that the parent [com.beancounter.common.contracts.TrnPayload]
 * can de-duplicate those objects across many trns in a single response.
 *
 * Persistence is handled by the [Trn] @Entity; this class is purely transport.
 */
data class TrnDto(
    val id: String,
    val trnType: TrnType,
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val tradeDate: LocalDate,
    val assetId: String,
    val quantity: BigDecimal = BigDecimal.ZERO,
    val callerRef: CallerRef? = null,
    val price: BigDecimal? = null,
    val tradeAmount: BigDecimal = BigDecimal.ZERO,
    val tradeCurrencyCode: String,
    val cashAssetId: String? = null,
    val cashCurrencyCode: String? = null,
    val tradeCashRate: BigDecimal = BigDecimal.ZERO,
    val tradeBaseRate: BigDecimal = BigDecimal.ONE,
    val tradePortfolioRate: BigDecimal = BigDecimal.ONE,
    val cashAmount: BigDecimal = BigDecimal.ZERO,
    val portfolioId: String,
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd"
    )
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val settleDate: LocalDate? = null,
    val fees: BigDecimal = BigDecimal.ZERO,
    val tax: BigDecimal = BigDecimal.ZERO,
    val comments: String? = null,
    val brokerId: String? = null,
    val version: String = Trn.VERSION,
    val status: TrnStatus = TrnStatus.SETTLED,
    val modelId: String? = null,
    val subAccounts: Map<String, BigDecimal>? = null,
    val createdBy: SystemUser? = null
) {
    companion object {
        @JvmStatic
        fun from(trn: Trn): TrnDto =
            TrnDto(
                id = trn.id,
                trnType = trn.trnType,
                tradeDate = trn.tradeDate,
                assetId = trn.asset.id,
                quantity = trn.quantity,
                callerRef = trn.callerRef,
                price = trn.price,
                tradeAmount = trn.tradeAmount,
                tradeCurrencyCode = trn.tradeCurrency.code,
                cashAssetId = trn.cashAsset?.id,
                cashCurrencyCode = trn.cashCurrency?.code,
                tradeCashRate = trn.tradeCashRate,
                tradeBaseRate = trn.tradeBaseRate,
                tradePortfolioRate = trn.tradePortfolioRate,
                cashAmount = trn.cashAmount,
                portfolioId = trn.portfolio.id,
                settleDate = trn.settleDate,
                fees = trn.fees,
                tax = trn.tax,
                comments = trn.comments,
                brokerId = trn.broker?.id,
                version = trn.version,
                status = trn.status,
                modelId = trn.modelId,
                subAccounts = trn.subAccounts,
                createdBy = trn.createdBy
            )
    }
}