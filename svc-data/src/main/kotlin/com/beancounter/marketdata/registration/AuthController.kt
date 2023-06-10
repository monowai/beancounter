package com.beancounter.marketdata.registration

import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.LoginRequest
import com.beancounter.auth.model.OpenIdResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * To support bc-shell.  User logs in via a CLI and needs a token. We don't want secrets in BCS,
 * so we keep all that server side.  PASSWORD auth is not a long term solution.
 */
@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(value = ["auth.enabled"], havingValue = "true", matchIfMissing = false)
class AuthController(val loginService: LoginService) {
    @PostMapping
    fun token(@RequestBody loginParams: LoginRequest): OpenIdResponse {
        return loginService.login(loginParams.user, loginParams.password)
    }
}
