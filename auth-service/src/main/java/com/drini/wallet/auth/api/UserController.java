package com.drini.wallet.auth.api;

import com.drini.wallet.auth.api.dto.UpdateProfileRequest;
import com.drini.wallet.auth.api.dto.UserLookupResponse;
import com.drini.wallet.auth.api.dto.UserProfileResponse;
import com.drini.wallet.auth.service.UserService;
import com.drini.wallet.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** User endpoints for signed-in users (JWT required). */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getProfile(CurrentUser.from(jwt).getUserId());
    }

    @PutMapping("/me")
    public UserProfileResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateDisplayName(CurrentUser.from(jwt).getUserId(), request.getDisplayName());
    }

    @GetMapping("/lookup")
    public UserLookupResponse lookup(@RequestParam String identifier) {
        return userService.lookup(identifier);
    }
}
