package com.beancounter.common.model

import com.beancounter.common.utils.KeyGenUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * A named list of assets a user is watching. Owned by a [SystemUser]; holds no quantities.
 */
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_watchlist_owner_code",
            columnNames = ["owner_id", "code"]
        )
    ]
)
data class Watchlist(
    @Id
    val id: String = KeyGenUtils().id,
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnore
    val owner: SystemUser,
    var code: String,
    var name: String,
    // No default: an entity cannot resolve "today" in the configured zone. See WatchlistService.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val createdAt: LocalDate
)