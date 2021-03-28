package com.beancounter.shell.ingest

import com.beancounter.common.input.TrustedTrnImportRequest

interface TrnWriter {
    fun reset()
    fun write(trnRequest: TrustedTrnImportRequest)

    /**
     * if you're writer supports batching, this tells you when we're done processing.
     */
    fun flush()
    fun id(): String?
}
