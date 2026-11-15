package com.beancounter.marketdata.broker

import com.beancounter.common.contracts.Payload
import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * A brokerage firm known to every user: reference data that no user owns. A user's
 * own [com.beancounter.common.model.Broker] is an account with one.
 */
@Entity
@Table(
    name = "global_broker",
    uniqueConstraints = [UniqueConstraint(name = "uk_global_broker_code", columnNames = ["code"])]
)
data class GlobalBroker(
    @Id
    var id: String = KeyGenUtils().id,
    val code: String,
    val name: String,
    @Convert(converter = BrokerConfigConverter::class)
    @Column(nullable = false, columnDefinition = "TEXT")
    val config: BrokerConfig = BrokerConfig()
)

/**
 * What Beancounter knows about a brokerage firm beyond its name.
 *
 * @property aliases other names the firm is known by, as a user or a statement writes them
 * @property market the market code its listed holdings trade on
 * @property currency the currency it reports holdings in
 */
data class BrokerConfig(
    val aliases: List<String> = emptyList(),
    val market: String = "",
    val currency: String = ""
)

/** Stores [BrokerConfig] as a JSON document, so a new setting needs no new column. */
@Converter
class BrokerConfigConverter : AttributeConverter<BrokerConfig, String> {
    override fun convertToDatabaseColumn(attribute: BrokerConfig): String = mapper.writeValueAsString(attribute)

    override fun convertToEntityAttribute(dbData: String): BrokerConfig = mapper.readValue(dbData)

    private companion object {
        val mapper = jacksonObjectMapper()
    }
}

data class GlobalBrokersResponse(
    override var data: Collection<GlobalBroker>
) : Payload<Collection<GlobalBroker>>