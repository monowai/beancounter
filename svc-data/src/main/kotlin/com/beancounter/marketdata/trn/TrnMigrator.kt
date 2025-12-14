package com.beancounter.marketdata.trn

import com.beancounter.common.model.Trn
import org.springframework.stereotype.Service

/**
 * Support class to facilitate upgrades between versions of Trn objects.
 * Currently a NOOP - will be implemented when migrations are needed.
 */
@Service
class TrnMigrator {
    fun upgrade(trn: Trn): Trn = trn
}