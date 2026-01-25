package com.beancounter.marketdata.broker

import com.beancounter.common.contracts.BrokerResponse
import com.beancounter.common.contracts.BrokerWithAccountsResponse
import com.beancounter.common.contracts.BrokersResponse
import com.beancounter.common.contracts.BrokersWithAccountsResponse
import com.beancounter.common.input.BrokerInput
import com.beancounter.common.model.Broker
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TrnBrokerService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/brokers")
class BrokerController(
    private val brokerService: BrokerService,
    private val systemUserService: SystemUserService,
    private val trnBrokerService: TrnBrokerService
) {
    @GetMapping
    fun getBrokers(
        @RequestParam(defaultValue = "false") includeAccounts: Boolean
    ): ResponseEntity<*> {
        val owner = systemUserService.getOrThrow()
        return if (includeAccounts) {
            ResponseEntity.ok(BrokersWithAccountsResponse(brokerService.findByOwnerWithAccounts(owner)))
        } else {
            ResponseEntity.ok(BrokersResponse(brokerService.findByOwner(owner)))
        }
    }

    @GetMapping("/{id}")
    fun getBroker(
        @PathVariable id: String,
        @RequestParam(defaultValue = "false") includeAccounts: Boolean
    ): ResponseEntity<*> {
        val owner = systemUserService.getOrThrow()
        return if (includeAccounts) {
            val broker = brokerService.findByIdWithAccounts(id, owner)
            if (broker.isPresent) {
                ResponseEntity.ok(BrokerWithAccountsResponse(broker.get()))
            } else {
                ResponseEntity.notFound().build<Any>()
            }
        } else {
            val broker = brokerService.findById(id, owner)
            if (broker.isPresent) {
                ResponseEntity.ok(BrokerResponse(broker.get()))
            } else {
                ResponseEntity.notFound().build<Any>()
            }
        }
    }

    @PostMapping
    fun createBroker(
        @Valid @RequestBody input: BrokerInput
    ): ResponseEntity<BrokerWithAccountsResponse> {
        val owner = systemUserService.getOrThrow()
        val broker =
            Broker(
                name = input.name,
                owner = owner,
                accountNumber = input.accountNumber,
                notes = input.notes
            )
        val created = brokerService.create(broker, input.settlementAccounts)
        val withAccounts = brokerService.findByIdWithAccounts(created.id, owner)
        return ResponseEntity.status(HttpStatus.CREATED).body(BrokerWithAccountsResponse(withAccounts.get()))
    }

    @PatchMapping("/{id}")
    fun updateBroker(
        @PathVariable id: String,
        @RequestBody input: BrokerInput
    ): ResponseEntity<BrokerWithAccountsResponse> {
        val owner = systemUserService.getOrThrow()
        val updated =
            brokerService.update(
                id = id,
                owner = owner,
                name = input.name,
                accountNumber = input.accountNumber,
                notes = input.notes,
                settlementAccounts = input.settlementAccounts
            )
        val withAccounts = brokerService.findByIdWithAccounts(updated.id, owner)
        return ResponseEntity.ok(BrokerWithAccountsResponse(withAccounts.get()))
    }

    @DeleteMapping("/{id}")
    fun deleteBroker(
        @PathVariable id: String
    ): ResponseEntity<Void> {
        val owner = systemUserService.getOrThrow()
        brokerService.delete(id, owner)
        return ResponseEntity.noContent().build()
    }

    /**
     * Get transaction count for a broker.
     * Used to check if a broker can be deleted.
     */
    @GetMapping("/{id}/transactions/count")
    fun getTransactionCount(
        @PathVariable id: String
    ): ResponseEntity<Map<String, Long>> {
        val owner = systemUserService.getOrThrow()
        val count = brokerService.countTransactions(id, owner)
        return ResponseEntity.ok(mapOf("count" to count))
    }

    /**
     * Transfer all transactions from one broker to another.
     */
    @PostMapping("/{id}/transfer")
    fun transferTransactions(
        @PathVariable id: String,
        @RequestParam toBrokerId: String
    ): ResponseEntity<Map<String, Long>> {
        val count = trnBrokerService.transferBroker(id, toBrokerId)
        return ResponseEntity.ok(mapOf("transferred" to count))
    }
}