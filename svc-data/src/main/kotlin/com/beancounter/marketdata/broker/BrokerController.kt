package com.beancounter.marketdata.broker

import com.beancounter.common.contracts.BrokerResponse
import com.beancounter.common.contracts.BrokersResponse
import com.beancounter.common.input.BrokerInput
import com.beancounter.common.model.Broker
import com.beancounter.marketdata.registration.SystemUserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/brokers")
class BrokerController(
    private val brokerService: BrokerService,
    private val systemUserService: SystemUserService
) {
    @GetMapping
    fun getBrokers(): BrokersResponse {
        val owner = systemUserService.getOrThrow()
        return BrokersResponse(brokerService.findByOwner(owner))
    }

    @GetMapping("/{id}")
    fun getBroker(
        @PathVariable id: String
    ): ResponseEntity<BrokerResponse> {
        val owner = systemUserService.getOrThrow()
        val broker = brokerService.findById(id, owner)
        return if (broker.isPresent) {
            ResponseEntity.ok(BrokerResponse(broker.get()))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping
    fun createBroker(
        @Valid @RequestBody input: BrokerInput
    ): ResponseEntity<BrokerResponse> {
        val owner = systemUserService.getOrThrow()
        val broker =
            Broker(
                name = input.name,
                owner = owner,
                accountNumber = input.accountNumber,
                notes = input.notes
            )
        val created = brokerService.create(broker)
        return ResponseEntity.status(HttpStatus.CREATED).body(BrokerResponse(created))
    }

    @PatchMapping("/{id}")
    fun updateBroker(
        @PathVariable id: String,
        @RequestBody input: BrokerInput
    ): ResponseEntity<BrokerResponse> {
        val owner = systemUserService.getOrThrow()
        val updated =
            brokerService.update(
                id = id,
                owner = owner,
                name = input.name,
                accountNumber = input.accountNumber,
                notes = input.notes
            )
        return ResponseEntity.ok(BrokerResponse(updated))
    }

    @DeleteMapping("/{id}")
    fun deleteBroker(
        @PathVariable id: String
    ): ResponseEntity<Void> {
        val owner = systemUserService.getOrThrow()
        brokerService.delete(id, owner)
        return ResponseEntity.noContent().build()
    }
}