package com.beancounter.marketdata.utils

import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.utils.BcJson
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

object RegistrationUtils {
    var authorityRoleConverter = AuthorityRoleConverter()

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
                    BcJson().objectMapper
                        .writeValueAsBytes(RegistrationRequest("user@testing.com"))
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
    }
}
