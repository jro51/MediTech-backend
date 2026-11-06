package com.medictech.auth_service.controller;

import com.medictech.auth_service.entity.User;
import com.medictech.auth_service.exception.GlobalExceptionHandler;
import com.medictech.auth_service.repository.UserRepository;
import com.medictech.auth_service.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que @Valid esté realmente conectado al controlador y que los errores
 * de autenticación/validación salgan con el mismo formato JSON que los demás servicios.
 */
@ExtendWith(MockitoExtension.class)
class AuthValidationTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-1234567890";

    @Mock
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", 60_000L);

        AuthController controller = new AuthController(userRepository, jwtUtils, new BCryptPasswordEncoder());

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private String registerJson(String name, String email, String password) {
        return """
                {"name":"%s","phone":"999888777","email":"%s","password":"%s"}
                """.formatted(name, email, password);
    }

    @Test
    void register_withInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Ana", "no-es-email", "secreto123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("email")));
    }

    @Test
    void register_withShortPassword_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Ana", "ana@botica.com", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("password")));
    }

    @Test
    void register_withBlankName_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("", "ana@botica.com", "secreto123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("name")));
    }

    @Test
    void register_withValidData_returns201() throws Exception {
        when(userRepository.existsByEmail("ana@botica.com")).thenReturn(false);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Ana", "ana@botica.com", "secreto123")))
                .andExpect(status().isCreated());
    }

    @Test
    void register_withExistingEmail_returns400AsJson() throws Exception {
        when(userRepository.existsByEmail("ana@botica.com")).thenReturn(true);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Ana", "ana@botica.com", "secreto123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El email ya está registrado"));
    }

    @Test
    void login_withWrongPassword_returns401AsJson() throws Exception {
        when(userRepository.findByEmail("ana@botica.com")).thenReturn(Optional.of(User.builder()
                .id(1L)
                .name("Ana")
                .email("ana@botica.com")
                .password(new BCryptPasswordEncoder().encode("secreto123"))
                .role("USER")
                .build()));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@botica.com","password":"incorrecta"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales incorrectas"));
    }

    @Test
    void login_withInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-es-email","password":"secreto123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("email")));
    }

    @Test
    void login_withMalformedBody_returns400AsJson() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es válido"));
    }

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        when(userRepository.findByEmail("ana@botica.com")).thenReturn(Optional.of(User.builder()
                .id(1L)
                .name("Ana")
                .email("ana@botica.com")
                .password(new BCryptPasswordEncoder().encode("secreto123"))
                .role("USER")
                .build()));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@botica.com","password":"secreto123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("USER"));
    }
}
