package com.frotafantasma.analytics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI frotaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Frota Fantasma Analytics")
                .version("0.1.0")
                .description("Fase 1: consulta do clã configurado no backend. "
                        + "O token da Supercell é exclusivo do servidor. "
                        + "O sistema apoia decisões humanas e não promove ou expulsa membros."));
    }
}
