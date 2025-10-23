package com.xksgroup.m3u8encoderv2.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        // Advertise both public gateway and local dev servers
        Server httpsGateway = new Server()
                .url("https://gateway.mytelevision.tv/m3u8-encoder")
                .description("Gateway HTTPS base");

        Server localDev = new Server()
                .url("http://localhost:8080")
                .description("Local development");

        return new OpenAPI()
                .servers(List.of(httpsGateway, localDev));
    }
}


