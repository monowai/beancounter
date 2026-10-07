package com.beancounter.agent.conversation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The conversation REST surface end to end: security, JSON shape, and owner
 * scoping against the real store. Only the svc-data `/me` lookup is stubbed.
 */
@SpringBootTest(
    properties = [
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://beancounter.eu.auth0.com/",
        "auth.audience=https://holdsworth.app"
    ]
)
@AutoConfigureMockMvc
class ConversationControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var service: ConversationService

    @MockitoBean
    private lateinit var owner: ConversationOwner

    @MockitoBean
    private lateinit var titler: ConversationTitler

    private val me = "owner-me"
    private val other = "owner-other"

    @BeforeEach
    fun actAsMe() {
        whenever(owner.id()).thenReturn(me)
        service.deleteAll(me)
        service.deleteAll(other)
    }

    private fun seeded(
        ownerId: String,
        question: String
    ): String {
        val id = service.create(ownerId).id
        service.appendUser(ownerId, id, question, false)
        service.appendAssistant(ownerId, id, "answer to $question", null)
        return id
    }

    @Test
    fun `should reject an unauthenticated caller`() {
        mockMvc.perform(get("/agent/conversations")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `should create an empty conversation for the caller`() {
        mockMvc
            .perform(post("/agent/conversations").with(jwt()))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.id").isNotEmpty)
    }

    private fun seed(body: String) =
        post("/agent/conversations")
            .with(jwt())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    @Test
    fun `should create a conversation seeded with a prior exchange`() {
        val body =
            """{"turns":[
              {"role":"user","content":"Full canned prompt","label":"Asset Review: ACME","deepThink":true},
              {"role":"assistant","content":"The answer"}]}"""

        val created =
            mockMvc
                .perform(seed(body))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.title").value("Asset Review ACME"))
                .andReturn()
                .response.contentAsString
        val id = Regex(""""id":"([^"]+)"""").find(created)!!.groupValues[1]

        mockMvc
            .perform(get("/agent/conversations/$id").with(jwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.messages.length()").value(2))
            .andExpect(jsonPath("$.data.messages[0].role").value("user"))
            .andExpect(jsonPath("$.data.messages[0].content").value("Full canned prompt"))
            .andExpect(jsonPath("$.data.messages[0].label").value("Asset Review: ACME"))
            .andExpect(jsonPath("$.data.messages[0].deepThink").value(true))
            .andExpect(jsonPath("$.data.messages[1].role").value("assistant"))
            .andExpect(jsonPath("$.data.messages[1].content").value("The answer"))
    }

    @Test
    fun `should still create an empty conversation from an empty turn list`() {
        mockMvc
            .perform(seed("""{"turns":[]}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.id").isNotEmpty)
        assertThat(service.list(me, 0, 10)).isEmpty()
    }

    @Test
    fun `should refuse a seed turn with an unknown role`() {
        mockMvc
            .perform(seed("""{"turns":[{"role":"system","content":"x"}]}"""))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should refuse a seed turn with blank content`() {
        mockMvc
            .perform(seed("""{"turns":[{"role":"user","content":"  "}]}"""))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should refuse more than 50 seed turns`() {
        val turns = (1..51).joinToString(",") { """{"role":"user","content":"q$it"}""" }

        mockMvc.perform(seed("""{"turns":[$turns]}""")).andExpect(status().isBadRequest)
    }

    @Test
    fun `should give a seeded conversation a generated title`() {
        val body =
            """{"turns":[
              {"role":"user","content":"Full canned prompt","label":"Asset Review: ACME"},
              {"role":"assistant","content":"The answer"}]}"""

        mockMvc.perform(seed(body)).andExpect(status().isCreated)

        verify(titler).suggest(
            eq(me),
            org.mockito.kotlin.any(),
            eq("Asset Review ACME"),
            eq(listOf("Asset Review: ACME")),
            eq("The answer")
        )
    }

    @Test
    fun `should list the caller's conversations with titles`() {
        seeded(me, "What is my runway?")
        seeded(other, "Not yours")

        mockMvc
            .perform(get("/agent/conversations").with(jwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].title").value("Runway"))
    }

    @Test
    fun `should return a conversation's turns in order`() {
        val id = seeded(me, "How did I do?")

        mockMvc
            .perform(get("/agent/conversations/$id").with(jwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.messages[0].role").value("user"))
            .andExpect(jsonPath("$.data.messages[0].content").value("How did I do?"))
            .andExpect(jsonPath("$.data.messages[1].role").value("assistant"))
            .andExpect(jsonPath("$.data.messages[1].timestamp").isNotEmpty)
    }

    @Test
    fun `should answer 404 for another owner's conversation`() {
        val theirs = seeded(other, "private")

        mockMvc.perform(get("/agent/conversations/$theirs").with(jwt())).andExpect(status().isNotFound)
        mockMvc.perform(delete("/agent/conversations/$theirs").with(jwt())).andExpect(status().isNotFound)
    }

    @Test
    fun `should rename a conversation`() {
        val id = seeded(me, "q")

        mockMvc
            .perform(
                patch("/agent/conversations/$id")
                    .with(jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"Runway"}""")
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.title").value("Runway"))
    }

    @Test
    fun `should refuse a blank title`() {
        val id = seeded(me, "q")

        mockMvc
            .perform(
                patch("/agent/conversations/$id")
                    .with(jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"   "}""")
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `should delete a conversation`() {
        val id = seeded(me, "q")

        mockMvc.perform(delete("/agent/conversations/$id").with(jwt())).andExpect(status().isNoContent)
        mockMvc.perform(get("/agent/conversations/$id").with(jwt())).andExpect(status().isNotFound)
    }

    @Test
    fun `should delete all of the caller's conversations for offboarding`() {
        seeded(me, "one")
        seeded(me, "two")
        val kept = seeded(other, "theirs")

        mockMvc
            .perform(delete("/agent/conversations").with(jwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deleted").value(2))

        whenever(owner.id()).thenReturn(other)
        mockMvc
            .perform(get("/agent/conversations/$kept").with(jwt()))
            .andExpect(status().isOk)
    }
}