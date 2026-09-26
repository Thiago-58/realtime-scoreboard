package br.com.scoreboard.rest;

import br.com.scoreboard.service.AuditoriaService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * ============================================================================
 * CLASSE: AuditoriaResource
 * PACOTE: br.com.scoreboard.rest
 * ============================================================================
 *
 * RESPONSABILIDADE:
 * Endpoint REST para consulta da trilha de auditoria do sistema.
 * Permite auditar todas as ações ocorridas nas partidas e identificar sua origem.
 */
@Path("/auditoria")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Auditoria", description = "Consulta do histórico de alterações e ações no sistema")
public class AuditoriaResource {

    @Inject
    private AuditoriaService auditoriaService;

    @GET
    @Operation(summary = "Consultar auditoria", description = "Retorna histórico de criações, gols, alterações de status e exclusões")
    public Response listar(@QueryParam("usuario") String usuario,
                           @QueryParam("tipoAcao") String tipoAcao,
                           @QueryParam("dataInicio") String dataInicio,
                           @QueryParam("dataFim") String dataFim) {
        return Response.ok(auditoriaService.consultar(usuario, tipoAcao, dataInicio, dataFim)).build();
    }
}
