package com.meditech.api_gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-1234567890";

    private JwtAuthenticationFilter filter;
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
    }

    private String token(String secret, long expiresInMillis) {
        return Jwts.builder()
                .setSubject("ana@botica.com")
                .claim("role", "ADMIN")
                .claim("userId", "42")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiresInMillis))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request.build());
    }

    private void run(MockServerWebExchange exchange) {
        filter.filter(exchange, chain).block();
    }

    @Test
    void publicCatalogGet_isForwardedWithoutToken() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products"));

        run(exchange);

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void publicRoute_stripsIdentityHeadersSentByTheClient() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Id", "1")
                .header("X-User-Email", "hacker@evil.com"));

        run(exchange);

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.containsKey("X-User-Role")).isFalse();
        assertThat(headers.containsKey("X-User-Id")).isFalse();
        assertThat(headers.containsKey("X-User-Email")).isFalse();
    }

    @Test
    void loginAndRegister_arePublic() {
        run(exchange(MockServerHttpRequest.post("/api/auth/login")));
        assertThat(forwarded.getAndSet(null)).isNotNull();

        run(exchange(MockServerHttpRequest.post("/api/auth/register")));
        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void protectedRoute_withoutToken_returns401() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/purchases/buy"));

        run(exchange);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void protectedRoute_withMalformedHeader_returns401() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/purchases/buy")
                .header(HttpHeaders.AUTHORIZATION, "Token abc"));

        run(exchange);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void protectedRoute_withGarbageToken_returns401() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/purchases/buy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer esto-no-es-un-jwt"));

        run(exchange);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedRoute_withTokenSignedByAnotherSecret_returns401() {
        String forged = token("otro-secreto-distinto-pero-largo-de-al-menos-256-bits-0987654321", 60_000);
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/purchases/buy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged));

        run(exchange);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void protectedRoute_withExpiredToken_returns401() {
        String expired = token(SECRET, -60_000);
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/purchases/buy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired));

        run(exchange);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedRoute_withValidToken_injectsIdentityHeaders() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/purchases/buy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET, 60_000))
                // Un cliente malicioso intenta suplantar a otro usuario: el gateway lo sobrescribe
                .header("X-User-Id", "999")
                .header("X-User-Role", "USER"));

        run(exchange);

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("42");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("ADMIN");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("ana@botica.com");
    }

    @Test
    void inventoryAlerts_requireToken_evenThoughProductGetsArePublic() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products/alerts"));

        run(exchange);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void inventoryAlerts_withValidToken_areForwarded() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products/alerts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET, 60_000)));

        run(exchange);

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    void productCreation_withoutToken_returns401() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/products"));

        run(exchange);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void internalStockEndpoints_areHiddenFromOutsideEvenWithValidToken() {
        for (String path : new String[]{
                "/api/products/1/reduce-stock", "/api/products/1/restore-stock", "/api/products/1/check-stock"}) {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.put(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET, 60_000)));

            run(exchange);

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void filterRunsBeforeOtherFilters() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }
}
