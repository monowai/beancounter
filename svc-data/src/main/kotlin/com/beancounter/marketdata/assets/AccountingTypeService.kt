package com.beancounter.marketdata.assets

import com.beancounter.common.model.AccountingType
import com.beancounter.common.model.Currency
import com.beancounter.common.utils.KeyGenUtils
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Manages AccountingType entities. Each unique (category, currency) pair
 * has exactly one AccountingType row.
 */
@Service
@Transactional
class AccountingTypeService(
    private val accountingTypeRepository: AccountingTypeRepository,
    private val keyGenUtils: KeyGenUtils
) {
    fun getOrCreate(
        category: String,
        currency: Currency,
        boardLot: Int = 1,
        settlementDays: Int = 1
    ): AccountingType =
        accountingTypeRepository
            .findByCategoryAndCurrency(category.uppercase(), currency)
            .orElseGet {
                try {
                    accountingTypeRepository.save(
                        AccountingType(
                            id = keyGenUtils.id,
                            category = category.uppercase(),
                            currency = currency,
                            boardLot = boardLot,
                            settlementDays = settlementDays
                        )
                    )
                } catch (e: DataIntegrityViolationException) {
                    accountingTypeRepository
                        .findByCategoryAndCurrency(category.uppercase(), currency)
                        .orElseThrow { e }
                }
            }
}