package com.beancounter.marketdata.assets

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
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
    private val assetRepository: AssetRepository,
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

    fun findAll(): Collection<AccountingType> = accountingTypeRepository.findAll().toList()

    fun findById(id: String): AccountingType =
        accountingTypeRepository.findById(id).orElseThrow {
            NotFoundException("AccountingType not found: $id")
        }

    fun update(
        id: String,
        boardLot: Int?,
        settlementDays: Int?
    ): AccountingType {
        val existing = findById(id)
        val updated =
            existing.copy(
                boardLot = boardLot ?: existing.boardLot,
                settlementDays = settlementDays ?: existing.settlementDays
            )
        return accountingTypeRepository.save(updated)
    }

    fun delete(id: String) {
        val existing = findById(id)
        val usage = assetRepository.countByAccountingTypeId(id)
        if (usage > 0) {
            throw BusinessException(
                "Cannot delete $existing — $usage asset(s) reference it"
            )
        }
        accountingTypeRepository.delete(existing)
    }
}