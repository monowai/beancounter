package com.beancounter.common.model

import com.beancounter.common.utils.KeyGenUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * Tracks a feature discovery action by a user for explorer milestones.
 * Each action is recorded once (idempotent).
 */
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_explorer_action",
            columnNames = ["owner_id", "action_id"]
        )
    ]
)
data class UserExplorerAction(
    @Id
    val id: String = KeyGenUtils().id,
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnore
    val owner: SystemUser,
    val actionId: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val recordedAt: LocalDate = LocalDate.now()
)