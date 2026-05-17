package com.beancounter.common.contracts

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Broker
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnDto
import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Normalised transport envelope for a collection of transactions.
 *
 * Each [TrnDto] in [trns] carries id references for its asset, portfolio,
 * currency and broker. The actual objects are listed once in the
 * corresponding map, deduplicating large nested objects (Asset embeds
 * Market + Currency; Portfolio embeds two Currency + SystemUser; Broker
 * embeds SystemUser) across many trns in a single response.
 */
data class TrnPayload(
    val trns: List<TrnDto> = emptyList(),
    val assets: Map<String, Asset> = emptyMap(),
    val portfolios: Map<String, Portfolio> = emptyMap(),
    val currencies: Map<String, Currency> = emptyMap(),
    val brokers: Map<String, Broker> = emptyMap()
) {
    fun asset(trn: TrnDto): Asset? = assets[trn.assetId]

    fun portfolio(trn: TrnDto): Portfolio? = portfolios[trn.portfolioId]

    fun cashAsset(trn: TrnDto): Asset? = trn.cashAssetId?.let { assets[it] }

    fun tradeCurrency(trn: TrnDto): Currency? = currencies[trn.tradeCurrencyCode]

    fun cashCurrency(trn: TrnDto): Currency? = trn.cashCurrencyCode?.let { currencies[it] }

    fun broker(trn: TrnDto): Broker? = trn.brokerId?.let { brokers[it] }

    /**
     * Re-hydrates the envelope into a list of [Trn] entities. Consumer-side
     * helper for code that already operates on the fat shape and is not
     * worth refactoring in this iteration. Each Trn is reconstructed by
     * looking up its asset / portfolio / currency / broker references in
     * the envelope.
     */
    fun toTrns(): List<Trn> =
        trns.map { dto ->
            Trn(
                id = dto.id,
                trnType = dto.trnType,
                tradeDate = dto.tradeDate,
                asset = assets.getValue(dto.assetId),
                quantity = dto.quantity,
                callerRef = dto.callerRef,
                price = dto.price,
                tradeAmount = dto.tradeAmount,
                tradeCurrency = currencies.getValue(dto.tradeCurrencyCode),
                cashAsset = dto.cashAssetId?.let { assets[it] },
                cashCurrency = dto.cashCurrencyCode?.let { currencies[it] },
                tradeCashRate = dto.tradeCashRate,
                tradeBaseRate = dto.tradeBaseRate,
                tradePortfolioRate = dto.tradePortfolioRate,
                cashAmount = dto.cashAmount,
                portfolio = portfolios.getValue(dto.portfolioId),
                settleDate = dto.settleDate,
                fees = dto.fees,
                tax = dto.tax,
                comments = dto.comments,
                broker = dto.brokerId?.let { brokers[it] },
                version = dto.version,
                status = dto.status,
                modelId = dto.modelId,
                subAccounts = dto.subAccounts,
                createdBy = dto.createdBy
            )
        }

    companion object {
        @JvmStatic
        fun from(trns: Collection<Trn>): TrnPayload {
            val assets = linkedMapOf<String, Asset>()
            val portfolios = linkedMapOf<String, Portfolio>()
            val currencies = linkedMapOf<String, Currency>()
            val brokers = linkedMapOf<String, Broker>()
            val dtos = mutableListOf<TrnDto>()
            for (trn in trns) {
                assets.putIfAbsent(trn.asset.id, trn.asset)
                trn.cashAsset?.let { assets.putIfAbsent(it.id, it) }
                portfolios.putIfAbsent(trn.portfolio.id, trn.portfolio)
                currencies.putIfAbsent(trn.tradeCurrency.code, trn.tradeCurrency)
                trn.cashCurrency?.let { currencies.putIfAbsent(it.code, it) }
                trn.broker?.let { brokers.putIfAbsent(it.id, it) }
                dtos.add(TrnDto.from(trn))
            }
            return TrnPayload(
                trns = dtos,
                assets = assets,
                portfolios = portfolios,
                currencies = currencies,
                brokers = brokers
            )
        }
    }
}