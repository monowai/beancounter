package com.beancounter.agent.conversation

import com.beancounter.agent.conversation.ConversationService.Companion.ROLE_ASSISTANT
import com.beancounter.agent.conversation.ConversationService.Companion.ROLE_USER
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The caller's saved agent conversations. Turns are written by the query
 * endpoints when a request carries a `conversationId`; this surface lists,
 * reads, renames and deletes them. A client can also create one already
 * holding an exchange that happened statelessly (a Quick Analysis the user
 * then followed up on) by posting seed turns; the seeded answer earns the
 * same generated title a live first answer would.
 */
@RestController
@RequestMapping("/agent/conversations")
@Tag(name = "Conversations", description = "Saved agent chat history")
class ConversationController(
    private val service: ConversationService,
    private val owner: ConversationOwner,
    private val titler: ConversationTitler
) {
    private companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_SEED_TURNS = 50
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary =
            "Start a conversation, empty or seeded with prior turns (max 50); it is listed once it holds a turn"
    )
    fun create(
        @RequestBody(required = false) request: CreateConversationRequest?
    ): ConversationSummaryResponse {
        val turns = request?.turns.orEmpty()
        validate(turns)
        val ownerId = owner.id()
        val conversation = service.create(ownerId)
        if (turns.isNotEmpty()) seed(ownerId, conversation.id, turns)
        return ConversationSummaryResponse(
            ConversationSummary(
                conversation.id,
                if (turns.isEmpty()) conversation.title else service.titleOf(ownerId, conversation.id),
                conversation.createdAt,
                conversation.updatedAt
            )
        )
    }

    private fun validate(turns: List<SeedTurn>) {
        require(turns.size <= MAX_SEED_TURNS) { "A conversation can be seeded with at most $MAX_SEED_TURNS turns" }
        turns.forEach {
            require(it.role == ROLE_USER || it.role == ROLE_ASSISTANT) { "Seed turn role must be user or assistant" }
            require(it.content.isNotBlank()) { "Seed turn content cannot be blank" }
        }
    }

    /** Store [turns] in order, then title the conversation as a live first answer would. */
    private fun seed(
        ownerId: String,
        id: String,
        turns: List<SeedTurn>
    ) {
        turns.forEach {
            if (it.role == ROLE_USER) {
                service.appendUser(ownerId, id, it.content, it.deepThink, it.label)
            } else {
                service.appendAssistant(ownerId, id, it.content, null)
            }
        }
        val answer = turns.lastOrNull { it.role == ROLE_ASSISTANT } ?: return
        val questions = turns.filter { it.role == ROLE_USER }.map { it.label ?: it.content }
        titler.suggest(ownerId, id, service.titleOf(ownerId, id), questions, answer.content)
    }

    @GetMapping
    @Operation(summary = "The caller's conversations, most recently active first")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int
    ): ConversationsResponse =
        ConversationsResponse(service.list(owner.id(), page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE)))

    @GetMapping("/{id}")
    @Operation(summary = "One conversation with its turns, oldest first")
    fun get(
        @PathVariable id: String
    ): ConversationResponse = ConversationResponse(service.get(owner.id(), id))

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a conversation")
    fun rename(
        @PathVariable id: String,
        @RequestBody request: RenameConversationRequest
    ): ConversationResponse {
        val ownerId = owner.id()
        service.rename(ownerId, id, request.title)
        return ConversationResponse(service.get(ownerId, id))
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a conversation and its turns")
    fun delete(
        @PathVariable id: String
    ) = service.delete(owner.id(), id)

    @DeleteMapping
    @Operation(summary = "Delete all of the caller's conversations (offboarding)")
    fun deleteAll(): DeletedConversationsResponse = DeletedConversationsResponse(service.deleteAll(owner.id()))
}

/** One turn to seed; [label] and [deepThink] only apply to a user turn. */
data class SeedTurn(
    val role: String,
    val content: String,
    val label: String? = null,
    val deepThink: Boolean = false
)

data class CreateConversationRequest(
    val turns: List<SeedTurn> = emptyList()
)

data class RenameConversationRequest(
    val title: String
)

data class ConversationSummaryResponse(
    val data: ConversationSummary
)

data class ConversationsResponse(
    val data: List<ConversationSummary>
)

data class ConversationResponse(
    val data: ConversationDetail
)

data class DeletedConversationsResponse(
    val deleted: Int
)