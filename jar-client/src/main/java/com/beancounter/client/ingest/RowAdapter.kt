package com.beancounter.client.ingest

import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnImportRequest

interface RowAdapter {
    fun transform(trustedTrnImportRequest: TrustedTrnImportRequest?): TrnInput?
}
