package com.beancounter.marketdata.trn

import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Trn

/**
 * Retrieve a [Trn] by id or throw [NotFoundException] with a consistent message.
 * Deduplicates the six verbatim `findById(...).orElseThrow { NotFoundException(...) }`
 * call sites across TrnService, TrnSettlementService.
 */
fun TrnRepository.getOrThrow(trnId: String): Trn =
    findById(trnId).orElseThrow { NotFoundException("Transaction not found: $trnId") }