package com.beancounter.common.utils

import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Ways that BC details with unset and null values.
 */
@Service
class NumberUtils {
    fun isSet(value: BigDecimal?) = !isUnset(value)

    // Null and Zero are treated as "unSet"
    fun isUnset(value: BigDecimal?) = value == null || BigDecimal.ZERO.compareTo(value) == 0
}