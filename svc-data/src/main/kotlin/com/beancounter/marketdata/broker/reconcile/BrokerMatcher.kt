package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Broker
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.broker.BrokerService
import com.beancounter.marketdata.broker.GlobalBroker
import com.beancounter.marketdata.broker.GlobalBrokerService
import org.springframework.stereotype.Service

/**
 * The firm a statement is from and the caller's own broker it belongs to.
 *
 * @property firm the global broker, if known
 * @property broker the caller's broker, if exactly one fits
 */
data class BrokerMatch(
    val firm: GlobalBroker?,
    val broker: Broker?
)

/** Works out whose statement it is from the firm name and account digits printed on it. */
@Service
class BrokerMatcher(
    private val brokerService: BrokerService,
    private val globalBrokerService: GlobalBrokerService
) {
    /**
     * Only ever returns a broker [user] owns. The firm that broker is linked to wins
     * over the firm the statement prints.
     *
     * An account the user will create (a firm chosen, no broker id) matches no account:
     * guessing one from the firm would show another account's holdings as held.
     *
     * @throws NotFoundException when the statement names a broker id that is not [user]'s,
     * or a firm that does not exist
     */
    fun match(
        shown: ShownHoldings,
        user: SystemUser
    ): BrokerMatch {
        val firms = globalBrokerService.findAll()
        val chosenFirm =
            shown.globalBrokerId?.takeIf { it.isNotBlank() }?.let { id ->
                firms.firstOrNull { it.id == id } ?: throw NotFoundException("Brokerage firm not found: $id")
            }
        if (chosenFirm != null && shown.brokerId.isNullOrBlank()) return BrokerMatch(firm = chosenFirm, broker = null)
        val printed = firmNamed(shown.brokerName, firms)
        val broker = callersBroker(shown, user, printed)
        val linked = broker?.globalBrokerId?.let { id -> firms.firstOrNull { it.id == id } }
        return BrokerMatch(firm = linked ?: printed, broker = broker)
    }

    private fun firmNamed(
        name: String?,
        firms: List<GlobalBroker>
    ): GlobalBroker? {
        val wanted = name?.trim().orEmpty()
        if (wanted.isEmpty()) return null
        return firms.firstOrNull { firm ->
            (firm.config.aliases + firm.name + firm.code).any { it.trim().equals(wanted, ignoreCase = true) }
        }
    }

    private fun callersBroker(
        shown: ShownHoldings,
        user: SystemUser,
        printed: GlobalBroker?
    ): Broker? {
        val chosen = shown.brokerId?.takeIf { it.isNotBlank() }
        if (chosen != null) {
            return brokerService.findById(chosen, user).orElseThrow { NotFoundException("Broker not found: $chosen") }
        }
        val owned = brokerService.findByOwner(user)
        val digits = digitsOf(shown.accountNumber)
        val byDigits = if (digits.isEmpty()) owned else owned.filter { digitsOf(it.accountNumber).endsWith(digits) }
        val byFirm = byDigits.filter { printed != null && it.globalBrokerId == printed.id }
        val narrowed = digits.isNotEmpty() || byFirm.isNotEmpty()
        return byFirm.ifEmpty { byDigits }.singleOrNull().takeIf { narrowed }
    }

    private fun digitsOf(accountNumber: String?): String = accountNumber.orEmpty().filter { it.isDigit() }
}