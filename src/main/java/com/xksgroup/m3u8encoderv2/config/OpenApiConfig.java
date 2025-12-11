package com.xksgroup.m3u8encoderv2.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {

        // Serveurs
        Server httpsGateway = new Server()
                .url("https://gateway.mytelevision.tv")
                .description("Passerelle HTTPS de production");

        Server localDev = new Server()
                .url("http://localhost:8080")
                .description("Développement local");

        Server localGatewayServer = new Server()
                .url("http://localhost:8765")
                .description("Passerelle locale de développement");

        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("bearerAuth");

        return new OpenAPI()
                .info(new Info()
                        .title("API M3U8 Encoder")
                        .version("v2")
                        .description("Documentation de l'API pour le service d'encodage M3U8"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerScheme))
                .addSecurityItem(securityRequirement)
                .servers(List.of(httpsGateway, localDev, localGatewayServer));
    }
}
