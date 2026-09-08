package com.dev.nexusfinance.dto;

import com.dev.nexusfinance.models.User;
import com.dev.nexusfinance.models.UserProfileImage;
import java.util.Base64;
import java.util.UUID;

public record UserView(UUID id, String name, String email, String cpf, String profileImage) {
    public static UserView from(User user, UserProfileImage profileImage) {
        String image = null;
        if (profileImage != null && profileImage.getData() != null && profileImage.getData().length > 0) {
            image = "data:" + profileImage.getContentType() + ";base64," + Base64.getEncoder().encodeToString(profileImage.getData());
        }
        return new UserView(user.getIdUser(), user.getName(), user.getEmail(), user.getCpf(), image);
    }
}
