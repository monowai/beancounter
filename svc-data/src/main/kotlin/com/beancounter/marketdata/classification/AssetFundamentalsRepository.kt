package com.beancounter.marketdata.classification

import com.beancounter.common.model.AssetFundamentals
import org.springframework.data.repository.CrudRepository

/** One [AssetFundamentals] row per asset; `save` on an existing id merges in place. */
interface AssetFundamentalsRepository : CrudRepository<AssetFundamentals, String>