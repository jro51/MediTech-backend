package com.medictech.auth_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MediTech · Auth Service")
                .version("1.0")
                .description("Registro de usuarios y login. Devuelve un JWT con rol y userId."));
    }
}
