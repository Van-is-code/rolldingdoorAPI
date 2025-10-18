package api.rollingdoor.controller;

import api.rollingdoor.dto.request.LoginRequest;
import api.rollingdoor.dto.request.RegisterRequest;
import api.rollingdoor.dto.response.AuthResponse;
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
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.registerUser(registerRequest);
        // SỬA LẠI: Trả về một Map (sẽ được Spring chuyển thành JSON)
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
        // Hoặc trả về một đối tượng DTO đơn giản nếu muốn
        // return ResponseEntity.ok(new MessageResponse("User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        String jwt = authService.loginUser(loginRequest);
        return ResponseEntity.ok(new AuthResponse(jwt));
    }
}