package com.medictech.auth_service.controller;

import com.medictech.auth_service.dto.AuthResponse;
import com.medictech.auth_service.dto.LoginRequest;
import com.medictech.auth_service.dto.RegisterRequest;
import com.medictech.auth_service.entity.User;
import com.medictech.auth_service.exception.InvalidCredentialsException;
import com.medictech.auth_service.repository.UserRepository;
import com.medictech.auth_service.security.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Registro e inicio de sesión")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final BCryptPasswordEncoder passwordEncoder;

    @Operation(summary = "Registrar usuario", description = "Crea un usuario con rol USER. El rol ADMIN se asigna directamente en la base de datos.")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {

        // Verifica si el email ya existe
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        // Construye el usuario con el password encriptado
        User user = User.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .build();

        userRepository.save(user);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("Usuario registrado correctamente");
    }

    @Operation(summary = "Iniciar sesión", description = "Devuelve el JWT que se envía como Authorization: Bearer <token>.")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {

        // Busca el usuario por email
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        // Verifica que exista y que el password coincida
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Credenciales incorrectas");
        }

        // Genera el token con email, role y userId
        String token = jwtUtils.generateToken(
                user.getEmail(),
                user.getRole(),
                user.getId()
        );

        // Devuelve la respuesta con el token y datos del usuario
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();

        return ResponseEntity.ok(response);
    }
}
