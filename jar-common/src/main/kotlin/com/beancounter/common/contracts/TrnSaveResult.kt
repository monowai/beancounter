package com.beancounter.common.contracts

import com.beancounter.common.model.Trn

/**
 * Internal result from TrnService.saveWithResult — persisted trns plus
 * any non-fatal warnings raised during auto-settle. Controllers wrap the
 * warnings into [TrnResponse] for the UI; legacy callers that don't care
 * about warnings can use the simpler [TrnService.save] overload.
 */
data class TrnSaveResult(
    val trns: List<Trn>,
    val warnings: List<String>
)
