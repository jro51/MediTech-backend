package com.medictech.product_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MediTech · Product Service")
                .version("1.0")
                .description("Catálogo de productos, control de stock, vencimientos y alertas "
                        + "de inventario para boticas."));
    }
}
