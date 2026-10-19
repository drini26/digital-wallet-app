package com.drini.wallet.auth.api;

import com.drini.wallet.auth.api.dto.LoginRequest;
import com.drini.wallet.auth.api.dto.LoginResponse;
import com.drini.wallet.auth.api.dto.MagicLinkRequest;
import com.drini.wallet.auth.api.dto.MessageResponse;
import com.drini.wallet.auth.api.dto.RegisterRequest;
import com.drini.wallet.auth.service.LoginService;
import com.drini.wallet.auth.service.MagicLinkService;
import com.drini.wallet.auth.service.SignUpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public sign-up and sign-in endpoints (no JWT needed). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String CHECK_INBOX = "If the address is valid, a sign-in link is on its way. Check your inbox.";

    private final SignUpService signUpService;
    private final MagicLinkService magicLinkService;
    private final LoginService loginService;

    public AuthController(SignUpService signUpService, MagicLinkService magicLinkService, LoginService loginService) {
        this.signUpService = signUpService;
        this.magicLinkService = magicLinkService;
        this.loginService = loginService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        signUpService.signUp(request.getDisplayName(), request.getIdentifier(), request.getAccountType());
        return new MessageResponse(CHECK_INBOX);
    }

    @PostMapping("/magic-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse requestMagicLink(@Valid @RequestBody MagicLinkRequest request) {
        magicLinkService.requestSignInLink(request.getIdentifier());
        return new MessageResponse(CHECK_INBOX);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request.getIdentifier(), request.getToken());
    }
}
