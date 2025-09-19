package com.beancounter.agent

import io.swagger.v3.oas.annotations.Operation
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Controller for serving the chat interface at the root path
 */
@Controller
class ChatController {
    @GetMapping("/")
    @Operation(
        summary = "Get the chat interface",
        description = "Redirects to the static chat interface for testing the agent."
    )
    fun getChatInterface(): String = "redirect:/chat.html"
}