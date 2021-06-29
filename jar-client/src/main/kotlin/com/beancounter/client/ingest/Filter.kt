package com.beancounter.client.ingest

import com.beancounter.common.model.Asset
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Determines if the asset code is in a comma separated filter string.
 */
@Service
class Filter(@Value("\${filter:#{null}}") filter: String?) {
    private val filteredAssets: MutableCollection<String> = ArrayList()
    private fun init(filter: String?) {
        if (filter != null) {
            val values = filter.split(",").toTypedArray()
            for (value in values) {
                filteredAssets.add(value.uppercase(Locale.getDefault()))
            }
        }
    }

    /**
     * if a filter has been set, then this will check if the asset code is to be included.
     */
    fun inFilter(asset: Asset): Boolean {
        return if (!filteredAssets.isEmpty()) {
            filteredAssets.contains(asset.code.uppercase(Locale.getDefault()))
        } else true
    }

    /**
     * Hint to determine if we're filtering assets at all.
     */
    fun hasFilter(): Boolean {
        return !filteredAssets.isEmpty()
    }

    init {
        init(filter)
    }
}
