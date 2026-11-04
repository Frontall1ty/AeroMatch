package com.aeromatch.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI aeroMatchOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AeroMatch API")
                .version("v1")
                .description("Air cargo shipment request, quote and booking coordination"));
    }
}
