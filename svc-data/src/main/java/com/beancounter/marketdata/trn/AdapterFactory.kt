package com.beancounter.marketdata.trn

import com.beancounter.client.ingest.RowAdapter
import com.beancounter.common.input.ImportFormat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class AdapterFactory {
    private lateinit var bcRowAdapter: RowAdapter
    private lateinit var shareSightAdapter: RowAdapter

    @Autowired()
    fun setShareSightAdapter(@Qualifier("shareSightRowAdapter") shareSightAdapter: RowAdapter) {
        this.shareSightAdapter = shareSightAdapter
    }

    @Autowired()
    fun setBcAdapter(@Qualifier("bcRowAdapter") bcRowAdapter: RowAdapter) {
        this.bcRowAdapter = bcRowAdapter
    }

    fun get(importFormat: ImportFormat): RowAdapter {
        if (importFormat == ImportFormat.SHARESIGHT) {
            return this.shareSightAdapter
        }
        return bcRowAdapter
    }
}
