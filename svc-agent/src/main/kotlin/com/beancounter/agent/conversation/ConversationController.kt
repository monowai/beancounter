package com.beancounter.agent.conversation

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
 * reads, renames and deletes them.
 */
@RestController
@RequestMapping("/agent/conversations")
@Tag(name = "Conversations", description = "Saved agent chat history")
class ConversationController(
    private val service: ConversationService,
    private val owner: ConversationOwner
) {
    private companion object {
        const val MAX_PAGE_SIZE = 100
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start an empty conversation; it is listed once it holds a turn")
    fun create(): ConversationSummaryResponse {
        val conversation = service.create(owner.id())
        return ConversationSummaryResponse(
            ConversationSummary(conversation.id, conversation.title, conversation.createdAt, conversation.updatedAt)
        )
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