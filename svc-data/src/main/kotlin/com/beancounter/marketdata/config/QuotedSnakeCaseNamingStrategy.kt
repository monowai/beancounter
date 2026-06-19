package com.beancounter.marketdata.config

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment

/**
 * Physical naming strategy that maps entity/property names to quoted snake_case
 * identifiers (e.g. SystemUser -> "system_user", googleId -> "google_id").
 *
 * Restores the pre-Spring-Boot-4 behaviour after two Hibernate 7 changes:
 *  - Boot 4 no longer defaults the physical strategy to snake_case.
 *  - `globally_quoted_identifiers` now pre-quotes logical names, and the stock
 *    [CamelCaseToUnderscoresNamingStrategy] skips already-quoted identifiers, so
 *    enabling it silently disables the snake_case conversion.
 *
 * This strategy converts first (via the superclass) and then forces quoting, so
 * reserved words stay valid — notably `system_user`, which H2's MySQL test mode
 * treats as the reserved `SYSTEM_USER` function when unquoted. The result matches
 * the Flyway-managed snake_case schema in every dialect.
 */
class QuotedSnakeCaseNamingStrategy : CamelCaseToUnderscoresNamingStrategy() {
    override fun toPhysicalCatalogName(
        name: Identifier?,
        context: JdbcEnvironment
    ): Identifier? = quote(super.toPhysicalCatalogName(name, context))

    override fun toPhysicalSchemaName(
        name: Identifier?,
        context: JdbcEnvironment
    ): Identifier? = quote(super.toPhysicalSchemaName(name, context))

    override fun toPhysicalTableName(
        name: Identifier?,
        context: JdbcEnvironment
    ): Identifier? = quote(super.toPhysicalTableName(name, context))

    override fun toPhysicalSequenceName(
        name: Identifier?,
        context: JdbcEnvironment
    ): Identifier? = quote(super.toPhysicalSequenceName(name, context))

    override fun toPhysicalColumnName(
        name: Identifier?,
        context: JdbcEnvironment
    ): Identifier? = quote(super.toPhysicalColumnName(name, context))

    private fun quote(id: Identifier?): Identifier? = if (id == null || id.isQuoted) id else Identifier(id.text, true)
}