package com.drini.wallet.auth.service;

import com.drini.wallet.auth.api.dto.UserLookupResponse;
import com.drini.wallet.auth.api.dto.UserProfileResponse;
import com.drini.wallet.auth.domain.Identifier;
import com.drini.wallet.auth.domain.User;
import com.drini.wallet.auth.repository.UserRepository;
import com.drini.wallet.common.error.ErrorCode;
import com.drini.wallet.common.error.WalletException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** User queries ("who am I", "who is this email/phone") and the profile update (PUT /users/me). */
@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public UserProfileResponse getProfile(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new WalletException(ErrorCode.NOT_FOUND, "User not found"));
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setDisplayName(user.getDisplayName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRole(user.getRole().name());
        response.setAccountType(user.getAccountType().name());
        return response;
    }

    /**
     * Changes the display name only (email, phone and role cannot be changed here). The current JWT keeps the old
     * name until the next sign-in, and the ledger keeps its own copy for the payment history.
     */
    public UserProfileResponse updateDisplayName(UUID userId, String rawDisplayName) {
        String displayName = SignUpService.checkDisplayName(rawDisplayName);
        User user = users.findById(userId)
                .orElseThrow(() -> new WalletException(ErrorCode.NOT_FOUND, "User not found"));
        user.setDisplayName(displayName);
        users.save(user);
        return getProfile(userId);
    }

    public UserLookupResponse lookup(String rawIdentifier) {
        Identifier identifier = Identifier.parse(rawIdentifier);
        User user = users.findByIdentifier(identifier)
                .orElseThrow(() -> new WalletException(ErrorCode.RECIPIENT_NOT_FOUND, "No user with this email or phone"));
        if (!user.isActive()) {
            throw new WalletException(ErrorCode.RECIPIENT_NOT_FOUND, "No user with this email or phone");
        }
        UserLookupResponse response = new UserLookupResponse();
        response.setUserId(user.getId());
        response.setDisplayName(user.getDisplayName());
        response.setAccountType(user.getAccountType().name());
        return response;
    }
}
