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
 * Records a milestone earned by a user.
 * Each milestone can be upgraded to a higher tier but never downgraded.
 */
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_milestone",
            columnNames = ["owner_id", "milestone_id"]
        )
    ]
)
data class UserMilestone(
    @Id
    val id: String = KeyGenUtils().id,
    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnore
    val owner: SystemUser,
    val milestoneId: String,
    var tier: Int = 1,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var earnedAt: LocalDate = LocalDate.now()
)