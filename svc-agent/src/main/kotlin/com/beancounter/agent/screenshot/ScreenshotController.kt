package com.beancounter.agent.screenshot

import com.beancounter.agent.AgentProvider
import com.beancounter.agent.TurnFailure
import com.beancounter.agent.TurnMetering
import com.beancounter.agent.config.AgentScopeAuthorizer
import com.beancounter.agent.conversation.ConversationOwner
import com.beancounter.agent.quota.TurnQuota
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

/**
 * Reads an uploaded brokerage screenshot into a proposal. It writes nothing: the
 * caller shows the proposal to its user and sends what they confirm to svc-data.
 * Open to the callers the agent is open to; a read counts as one turn for a
 * metered caller, and only once it has been answered.
 */
@RestController
@ConditionalOnProperty(name = [AgentProvider.PROPERTY], havingValue = AgentProvider.DEEPSEEK, matchIfMissing = true)
@RequestMapping("/agent/screenshot")
class ScreenshotController(
    private val reader: ScreenshotReader,
    private val scopeAuthorizer: AgentScopeAuthorizer,
    private val turnMetering: TurnMetering,
    private val quota: TurnQuota,
    private val owner: ConversationOwner
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun read(
        @RequestPart("file") file: MultipartFile
    ): ScreenshotProposal {
        scopeAuthorizer.authorize()
        val meter = admit()
        val screenshot = accepted(file)
        val proposal =
            try {
                reader.read(screenshot)
            } catch (e: UnreadableScreenshotException) {
                throw ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, e.message, e)
            } catch (e: VisionUnavailableException) {
                throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "The screenshot reader is unavailable", e)
            }
        meter?.let(quota::spend)
        return proposal
    }

    /**
     * The owner to count this read against, or null for a caller with no quota.
     * Refused as a live agent turn is ([TurnMetering.refusalFor]).
     */
    private fun admit(): String? {
        val meter = if (scopeAuthorizer.unlimited()) null else owner.id()
        turnMetering.refusalFor(meter)?.let { code ->
            throw ResponseStatusException(HttpStatus.valueOf(TurnFailure.statusFor(code)), TurnFailure.messageFor(code))
        }
        return meter
    }

    private fun accepted(file: MultipartFile): Screenshot {
        val mediaType = file.contentType?.lowercase()
        val refusal =
            when {
                file.isEmpty -> {
                    HttpStatus.BAD_REQUEST to "The file is empty"
                }
                mediaType == null || mediaType !in MEDIA_TYPES -> {
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE to "Upload a PNG, JPEG, WebP or GIF image"
                }
                file.size > MAX_BYTES -> {
                    HttpStatus.CONTENT_TOO_LARGE to "The image is larger than 8 MB"
                }
                else -> {
                    return Screenshot(file.bytes, mediaType)
                }
            }
        throw ResponseStatusException(refusal.first, refusal.second)
    }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        private val MEDIA_TYPES = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
    }
}