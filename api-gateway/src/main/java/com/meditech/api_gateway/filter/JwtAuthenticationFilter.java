package com.meditech.api_gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_USER_EMAIL = "X-User-Email";

    // Endpoints que solo deben usarse entre microservicios: nunca desde el exterior.
    private static final List<String> INTERNAL_ENDPOINTS = List.of(
            "/check-stock", "/reduce-stock", "/restore-stock"
    );

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        // 1. Endpoints internos: se ocultan como si no existieran
        if (isInternalEndpoint(path)) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        // 2. Rutas públicas: pasan sin token, pero se descartan los headers de identidad
        //    para que un cliente no pueda hacerse pasar por ADMIN enviándolos a mano.
        if (isPublicRoute(path, method)) {
            return chain.filter(stripIdentityHeaders(exchange));
        }

        // 3. Rutas protegidas: se exige "Authorization: Bearer <token>"
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = validateToken(token);

            ServerWebExchange modifiedExchange = exchange.mutate()
                    .request(r -> r
                            .header(HEADER_USER_EMAIL, claims.getSubject())
                            .header(HEADER_USER_ROLE, claims.get("role", String.class))
                            .header(HEADER_USER_ID, String.valueOf(claims.get("userId"))))
                    .build();

            return chain.filter(modifiedExchange);

        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isInternalEndpoint(String path) {
        return path.startsWith("/api/products")
                && INTERNAL_ENDPOINTS.stream().anyMatch(path::endsWith);
    }

    private boolean isPublicRoute(String path, String method) {
        // 1. Los GET a productos son públicos (catálogo), salvo las alertas de inventario
        if (path.startsWith("/api/products") && "GET".equals(method)) {
            return !path.startsWith("/api/products/alerts");
        }
        // 2. El login y register siempre son públicos
        return path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register");
    }

    private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(r -> r.headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_ROLE);
                    headers.remove(HEADER_USER_EMAIL);
                }))
                .build();
    }

    private Claims validateToken(String token) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
