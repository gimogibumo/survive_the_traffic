package com.survive_the_traffic.auth.controller;

import com.survive_the_traffic.auth.dto.LoginRequest;
import com.survive_the_traffic.auth.dto.LoginResponse;
import com.survive_the_traffic.auth.dto.SignupRequest;
import com.survive_the_traffic.auth.dto.SignupResponse;
import com.survive_the_traffic.auth.service.AuthService;
import com.survive_the_traffic.auth.service.SessionLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionLoginService sessionLoginService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
         SignupResponse response = authService.signup(request);

         return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        LoginResponse response = sessionLoginService.login(request,httpRequest,httpResponse);
        return ResponseEntity.ok(response);
    }
}
