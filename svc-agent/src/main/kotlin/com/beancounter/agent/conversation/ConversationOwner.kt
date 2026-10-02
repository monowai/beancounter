package com.beancounter.agent.conversation

import com.beancounter.client.services.RegistrationService
import org.springframework.stereotype.Component

/**
 * The calling user's `SystemUser.id`, resolved by svc-data from the request's
 * JWT. Conversations are owned by that id, never by the token's `sub`.
 * Call on the request thread: the lookup reads the caller's token from the
 * security context.
 */
@Component
class ConversationOwner(
    private val registrationService: RegistrationService
) {
    fun id(): String = registrationService.me().id
}