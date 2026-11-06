package com.medictech.purchase_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI purchaseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MediTech · Purchase Service")
                .version("1.0")
                .description("Reservas con recojo en tienda: crea pedidos, controla su estado y "
                        + "publica eventos a Kafka."));
    }
}
