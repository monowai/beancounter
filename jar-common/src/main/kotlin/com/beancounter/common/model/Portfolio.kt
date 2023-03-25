package com.beancounter.common.model

import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.UniqueConstraint

/**
 * Portfolio that owns transactions on behalf of a systemUser
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["code", "owner_id"])])
data class Portfolio(
    @Id val id: String,
    val code: String,
    val name: String = code,
    @ManyToOne val currency: Currency,
    @ManyToOne val base: Currency,
    @ManyToOne var owner: SystemUser? = null,

) {

    constructor(code: String) : this(code, code, code, Currency("USD"), Currency("USD"))

    constructor(code: String, currency: Currency) : this(code, code, code, currency, currency)

    constructor(code: String, currency: Currency, base: Currency, systemUser: SystemUser?) : this(
        code,
        code,
        code,
        currency,
        base,
        systemUser,
    )
}
