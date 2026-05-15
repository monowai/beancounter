package com.beancounter.marketdata.providers.eodhd.news

import org.springframework.data.repository.CrudRepository

/**
 * CRUD for [NewsFetch] metadata. The service reads by ticker to decide whether the cached articles
 * are still fresh enough; writes are upserts keyed on ticker.
 */
interface NewsFetchRepo : CrudRepository<NewsFetch, String>