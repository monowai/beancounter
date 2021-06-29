package com.beancounter.common.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.LocalDate
import javax.persistence.Entity
import javax.persistence.Id

@Entity
/**
 * User of this service that is Authenticated.  SystemUsers can own portfolios.
 */
data class SystemUser @ConstructorBinding constructor(
    @Id var id: String,
    var email: String,
    var active: Boolean = true,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    var since: LocalDate = LocalDate.now()
) {

    constructor(id: String, email: String = "") :
        this(id, email, true, LocalDate.now())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SystemUser

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
