package com.hms.auth.service;

import com.hms.auth.dto.LoginRequest;
import com.hms.auth.dto.LoginResponse;
import com.hms.auth.dto.RegisterRequest;
import com.hms.auth.entity.User;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    User register(RegisterRequest request);
}
