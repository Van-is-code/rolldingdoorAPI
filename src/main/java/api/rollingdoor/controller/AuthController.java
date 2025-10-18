package api.rollingdoor.controller;

import api.rollingdoor.dto.request.LoginRequest;
import api.rollingdoor.dto.request.RegisterRequest;
import api.rollingdoor.dto.response.AuthResponse;
import api.rollingdoor.dto.response.MessageResponse;
import api.rollingdoor.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.registerUser(registerRequest);
        return ResponseEntity.ok(new MessageResponse("User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        String jwt = authService.loginUser(loginRequest);
        return ResponseEntity.ok(new AuthResponse(jwt));
    }
}