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
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
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
    /**
     * A refusal with its reason: `code` is the stable code the client maps to its own
     * words, `message` the server's. Without this the default error body carries only
     * the status phrase ("Forbidden").
     */
    @ExceptionHandler(ScreenshotRefusal::class)
    fun refused(e: ScreenshotRefusal): ResponseEntity<Map<String, String?>> =
        ResponseEntity.status(e.statusCode).body(mapOf("code" to e.code, "message" to e.reason))

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
                throw if (e.refused) {
                    ScreenshotRefusal(HttpStatus.BAD_GATEWAY, VISION_REFUSED, READER_MISCONFIGURED, e)
                } else {
                    ScreenshotRefusal(
                        HttpStatus.BAD_GATEWAY,
                        VISION_UNAVAILABLE,
                        "The screenshot reader is unavailable",
                        e
                    )
                }
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
            throw ScreenshotRefusal(HttpStatus.valueOf(TurnFailure.statusFor(code)), code, TurnFailure.messageFor(code))
        }
        return meter
    }

    /** The upload as a screenshot, its format taken from its bytes and not from the declared content type. */
    private fun accepted(file: MultipartFile): Screenshot {
        val refusal =
            when {
                file.isEmpty -> {
                    HttpStatus.BAD_REQUEST to "The file is empty"
                }
                file.size > MAX_BYTES -> {
                    HttpStatus.CONTENT_TOO_LARGE to "The image is larger than 8 MB"
                }
                else -> {
                    val bytes = file.bytes
                    val format = ImageFormat.of(bytes)
                    if (format != null) return Screenshot(bytes, format.mediaType)
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE to "Upload a PNG, JPEG, WebP or GIF image"
                }
            }
        throw ResponseStatusException(refusal.first, refusal.second)
    }

    companion object {
        /** The gateway turned the vision call away: its key or model is not allowed. */
        const val VISION_REFUSED = "vision-refused"

        /** The vision model could not be reached or gave no answer. */
        const val VISION_UNAVAILABLE = "vision-unavailable"

        private const val READER_MISCONFIGURED =
            "The screenshot reader is not set up correctly, so nothing was read. Tell the administrator."
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}

/** A refused read, with the code the client shows its own words for. */
class ScreenshotRefusal(
    status: HttpStatus,
    val code: String,
    message: String,
    cause: Throwable? = null
) : ResponseStatusException(status, message, cause)