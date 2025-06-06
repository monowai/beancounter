package com.beancounter.marketdata.utils

import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

/**
 * Create a user.
 */
object RegistrationUtils {
    @JvmStatic
    fun registerUser(
        mockMvc: MockMvc,
        token: Jwt
    ): Jwt {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/register")
                    .with(
                        SecurityMockMvcRequestPostProcessors.jwt().jwt(token)
                    ).with(SecurityMockMvcRequestPostProcessors.csrf())
                    .content(
                        objectMapper
                            .writeValueAsBytes(RegistrationRequest())
                    ).contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        return token
    }
}