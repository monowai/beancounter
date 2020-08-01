package com.beancounter.marketdata.providers

import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.EnableTransactionManagement

@EnableTransactionManagement
@Service
class ScheduledValuation(private val priceRefresh: PriceRefresh) {
    lateinit var dateUtils: DateUtils


    companion object {
        private val log = LoggerFactory.getLogger(ScheduledValuation::class.java)
    }

}