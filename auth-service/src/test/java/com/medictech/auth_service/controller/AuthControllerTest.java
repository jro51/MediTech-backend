package com.medictech.auth_service.controller;

import com.medictech.auth_service.dto.AuthResponse;
import com.medictech.auth_service.dto.LoginRequest;
import com.medictech.auth_service.dto.RegisterRequest;
import com.medictech.auth_service.entity.User;
import com.medictech.auth_service.exception.InvalidCredentialsException;
import com.medictech.auth_service.repository.UserRepository;
import com.medictech.auth_service.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-1234567890";

    @Mock
    private UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private JwtUtils jwtUtils;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", 60_000L);
        controller = new AuthController(userRepository, jwtUtils, passwordEncoder);
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Ana");
        request.setPhone("999888777");
        request.setEmail("ana@botica.com");
        request.setPassword("secreto123");
        return request;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private User storedUser() {
        return User.builder()
                .id(1L)
                .name("Ana")
                .email("ana@botica.com")
                .password(passwordEncoder.encode("secreto123"))
                .role("USER")
                .build();
    }

    @Test
    void register_createsUserWithEncodedPasswordAndUserRole() {
        when(userRepository.existsByEmail("ana@botica.com")).thenReturn(false);

        ResponseEntity<?> response = controller.register(registerRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo("USER");
        assertThat(saved.getPassword()).isNotEqualTo("secreto123");
        assertThat(passwordEncoder.matches("secreto123", saved.getPassword())).isTrue();
    }

    @Test
    void register_withExistingEmail_throwsBadRequestAndDoesNotSave() {
        when(userRepository.existsByEmail("ana@botica.com")).thenReturn(true);

        assertThatThrownBy(() -> controller.register(registerRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El email ya está registrado");
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_withValidCredentials_returnsTokenAndUserData() {
        when(userRepository.findByEmail("ana@botica.com")).thenReturn(Optional.of(storedUser()));

        ResponseEntity<?> response = controller.login(loginRequest("ana@botica.com", "secreto123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse body = (AuthResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getEmail()).isEqualTo("ana@botica.com");
        assertThat(body.getRole()).isEqualTo("USER");
        assertThat(jwtUtils.validateToken(body.getToken())).isTrue();
    }

    @Test
    void login_withWrongPassword_throwsUnauthorized() {
        when(userRepository.findByEmail("ana@botica.com")).thenReturn(Optional.of(storedUser()));

        assertThatThrownBy(() -> controller.login(loginRequest("ana@botica.com", "incorrecta")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciales incorrectas");
    }

    @Test
    void login_withUnknownEmail_throwsUnauthorized() {
        when(userRepository.findByEmail("nadie@botica.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.login(loginRequest("nadie@botica.com", "secreto123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciales incorrectas");
    }
}
