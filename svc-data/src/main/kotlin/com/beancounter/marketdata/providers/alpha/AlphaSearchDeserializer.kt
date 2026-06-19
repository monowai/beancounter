package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.utils.BcJson
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

/**
 * Deserialize AlphaVantage JSON response to a BC standard response object.
 */
class AlphaSearchDeserializer : ValueDeserializer<AssetSearchResponse>() {
    @Throws(JacksonException::class)
    override fun deserialize(
        p: JsonParser,
        context: DeserializationContext
    ): AssetSearchResponse {
        val results: MutableCollection<AssetSearchResult> = ArrayList()
        val source = context.readTree(p)
        val metaData = source[BEST_MATCHES]
        if (metaData != null) {
            val collectionType =
                mapper.typeFactory
                    .constructCollectionType(
                        ArrayList::class.java,
                        HashMap::class.java
                    )
            val rows =
                mapper.readValue<Collection<Map<String, String>>>(
                    source[BEST_MATCHES].toString(),
                    collectionType
                )
            for (row in rows) {
                val searchResult =
                    AssetSearchResult(
                        row["1. symbol"]!!,
                        row["2. name"]!!,
                        row["3. type"]!!,
                        null,
                        row["8. currency"]
                    )
                results.add(searchResult)
            }
        }
        return AssetSearchResponse(results)
    }

    companion object {
        const val BEST_MATCHES = "bestMatches"
        private val mapper = BcJson.objectMapper
    }
}