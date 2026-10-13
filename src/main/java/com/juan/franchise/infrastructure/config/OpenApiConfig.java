package com.juan.franchise.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI franchiseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Franchise API")
                .version("v1")
                .description("Reactive API to manage franchises, branches and product stock."));
    }
}
