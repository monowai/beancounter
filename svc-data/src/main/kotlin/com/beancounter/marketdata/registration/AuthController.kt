package com.beancounter.marketdata.registration

import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.LoginRequest
import com.beancounter.auth.model.OpenIdResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(value = ["auth.enabled"], havingValue = "true", matchIfMissing = false)
class AuthController(val loginService: LoginService) {
    @PostMapping
    fun token(@RequestBody loginParams: LoginRequest): OpenIdResponse {
        return loginService.login(loginParams.user, loginParams.password)
    }
}
