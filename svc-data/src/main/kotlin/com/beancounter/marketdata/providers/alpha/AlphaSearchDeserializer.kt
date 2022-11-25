package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetSearchResult
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException

/**
 * Deserialize AlphaVantage JSON response to a BC standard response object.
 */
class AlphaSearchDeserializer : JsonDeserializer<AssetSearchResponse>() {
    @Throws(IOException::class)
    override fun deserialize(p: JsonParser, context: DeserializationContext): AssetSearchResponse {
        val results: MutableCollection<AssetSearchResult> = ArrayList()
        val source = p.codec.readTree<JsonNode>(p)
        val metaData = source[BEST_MATCHES]
        if (metaData != null) {
            val collectionType = mapper.typeFactory
                .constructCollectionType(ArrayList::class.java, HashMap::class.java)
            val rows = mapper.readValue<Collection<Map<String, String>>>(
                source[BEST_MATCHES].toString(),
                collectionType
            )
            for (row in rows) {
                val searchResult = AssetSearchResult(
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
        private val mapper = ObjectMapper()
    }
}
