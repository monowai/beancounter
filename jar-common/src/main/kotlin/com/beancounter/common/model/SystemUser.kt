package com.beancounter.common.model

import com.beancounter.common.utils.KeyGenUtils
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import java.time.LocalDate
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * User of this service that is Authenticated.  SystemUsers can own portfolios.
 */
@Entity
@Table
data class SystemUser(
    @Id var id: String = KeyGenUtils().id,
    val email: String = "testUser",
    var active: Boolean = true,
    val auth0: String = id,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val since: LocalDate = LocalDate.now(),
)
