package br.com.scoreboard.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * ============================================================================
 * CLASSE: ScoreboardApplication
 * PACOTE: br.com.scoreboard.rest
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Ponto de entrada da aplicação JAX-RS montado sob o prefixo /api.
 * Configura as informações OpenAPI exibidas na documentação Swagger.
 */
@ApplicationPath("/api")
@OpenAPIDefinition(
        info = @Info(
                title = "Realtime Scoreboard API",
                version = "1.0.0",
                description = "API REST de Gerenciamento e Atualização em Tempo Real de Placares de Jogos de Futebol",
                contact = @Contact(name = "Thiago", email = "thiago.magalhaesmo@gmail.com")
        )
)
public class ScoreboardApplication extends Application { }