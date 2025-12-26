package com.beancounter.marketdata.classification

import com.beancounter.common.model.ClassificationItem
import com.beancounter.common.model.ClassificationLevel
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface ClassificationItemRepository : CrudRepository<ClassificationItem, String> {
    fun findByStandardIdAndLevelAndCode(
        standardId: String,
        level: ClassificationLevel,
        code: String
    ): Optional<ClassificationItem>

    fun findByLevel(level: ClassificationLevel): List<ClassificationItem>
}