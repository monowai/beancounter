package com.beancounter.agent.review

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * One cached canned Asset Review: what [model] answered about
 * [market]:[ticker] on [reviewDate]. Shared by every caller: the review reads
 * no user data. A refreshed or expired review is overwritten in place.
 */
@Entity
@Table(name = "asset_review_cache")
class AssetReview(
    @Id
    val id: String = KeyGenUtils().id,
    @Column(nullable = false, length = 32)
    val market: String,
    @Column(nullable = false, length = 64)
    val ticker: String,
    @Column(nullable = false)
    val reviewDate: LocalDate,
    @Column(nullable = false, length = 100)
    val model: String,
    @Column(nullable = false, columnDefinition = "text")
    var content: String,
    @Column(nullable = false)
    var createdAt: Instant
)