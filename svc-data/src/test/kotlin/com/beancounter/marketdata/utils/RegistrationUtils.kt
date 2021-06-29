package com.beancounter.marketdata.utils

import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.Constants
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
    var authorityRoleConverter = AuthorityRoleConverter()
    var objectMapper = BcJson().objectMapper
    @JvmStatic
    fun registerUser(mockMvc: MockMvc, token: Jwt?) {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/register")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt().jwt(token)
                        .authorities(authorityRoleConverter)
                )
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .content(
                    objectMapper
                        .writeValueAsBytes(RegistrationRequest(Constants.systemUser.email))
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
    }
}
