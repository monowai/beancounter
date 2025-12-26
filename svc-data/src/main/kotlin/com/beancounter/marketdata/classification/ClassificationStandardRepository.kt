package com.beancounter.marketdata.classification

import com.beancounter.common.model.ClassificationStandard
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface ClassificationStandardRepository : CrudRepository<ClassificationStandard, String> {
    fun findByKey(key: String): Optional<ClassificationStandard>
}