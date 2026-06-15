package com.hms.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private String username;
    private String email;
    private String role;
    private long expiresIn;

    public static LoginResponse of(String token, String username, String email, String role, long expiresIn) {
        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .username(username)
                .email(email)
                .role(role)
                .expiresIn(expiresIn)
                .build();
    }
}
