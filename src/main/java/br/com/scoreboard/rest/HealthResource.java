package br.com.scoreboard.rest;

import br.com.scoreboard.cache.RedisCacheService;
import br.com.scoreboard.messaging.RabbitMQConnection;
import br.com.scoreboard.repository.PartidaRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ============================================================================
 * CLASSE: HealthResource
 * PACOTE: br.com.scoreboard.rest
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Endpoint de monitoramento de saúde (Health Check) do sistema.
 * 
 * O QUE ELE VERIFICA:
 * Testa ativamente a conectividade com as 3 dependências externas fundamentais:
 * 1. Banco de Dados PostgreSQL (executa 'SELECT 1')
 * 2. Broker RabbitMQ (testa se o socket AMQP está aberto e saudável)
 * 3. Cache Redis (executa comando 'PING' esperando resposta 'PONG')
 * 
 * CÓDIGOS HTTP RETORNADOS:
 * - 200 OK: Todos os 3 serviços estão funcionando (UP).
 * - 503 SERVICE UNAVAILABLE: Se pelo menos um serviço estiver fora (DOWN).
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Saúde do Sistema", description = "Monitoramento e status das conexões (PostgreSQL, Redis e RabbitMQ)")
public class HealthResource {

    @Inject
    PartidaRepository repo;

    @Inject
    RabbitMQConnection rabbit;

    @Inject
    RedisCacheService redis;

    /**
     * Retorna o status de saúde detalhado de cada serviço de infraestrutura.
     */
    @GET
    @Operation(summary = "Health Check Completo", description = "Verifica conectividade com PostgreSQL, Redis e RabbitMQ")
    @APIResponse(responseCode = "200", description = "Todos os serviços estão UP")
    @APIResponse(responseCode = "503", description = "Pelo menos um dos serviços está DOWN")
    public Response health() {
        boolean dbOk = repo.pingOk();
        boolean rabbitOk = rabbit.isOpen();
        boolean redisOk = redis.pingOk();
        boolean geralOk = dbOk && rabbitOk && redisOk;

        Map<String, String> status = new LinkedHashMap<>();
        status.put("status", geralOk ? "UP" : "DOWN");
        status.put("bancoPostgres", dbOk ? "UP" : "DOWN");
        status.put("cacheRedis", redisOk ? "UP" : "DOWN");
        status.put("mensageriaRabbitMQ", rabbitOk ? "UP" : "DOWN");

        return Response.status(geralOk ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE)
                .entity(status)
                .build();
    }
}