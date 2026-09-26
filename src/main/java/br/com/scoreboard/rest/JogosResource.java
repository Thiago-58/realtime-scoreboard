package br.com.scoreboard.rest;

import br.com.scoreboard.dto.AtualizarPlacarRequest;
import br.com.scoreboard.dto.AtualizarStatusRequest;
import br.com.scoreboard.dto.CriarPartidaRequest;
import br.com.scoreboard.dto.PartidaResponse;
import br.com.scoreboard.exception.RegraNegocioException;
import br.com.scoreboard.service.PartidaService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * ============================================================================
 * CLASSE: JogosResource
 * PACOTE: br.com.scoreboard.rest
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Controlador REST (JAX-RS) que expõe os endpoints HTTP da API de Jogos.
 * Cumpre integralmente os Requisitos de Backend (Seção 3) do desafio técnico:
 * 
 * ROTAS DISPONÍVEIS:
 * - POST   /jogos             -> Cria uma nova partida (HTTP 201)
 * - GET    /jogos             -> Lista partidas com filtros e paginação (HTTP 200)
 * - GET    /jogos/{id}        -> Busca detalhes de uma partida por ID (HTTP 200)
 * - PUT    /jogos/{id}/placar -> Atualiza placar de partida em andamento (HTTP 200)
 * - PUT    /jogos/{id}/status -> Altera o status da partida (HTTP 200)
 * - DELETE /jogos/{id}        -> Exclui uma partida do banco e do cache (HTTP 204)
 */
@Path("/jogos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Jogos", description = "Gerenciamento e atualização de partidas de futebol")
public class JogosResource {

    private static final String ORIGEM_API = "api-rest";

    @Inject
    PartidaService service;

    /**
     * POST /jogos
     * Cria uma nova partida com placar inicial e status EM_ANDAMENTO.
     * 
     * @param req DTO contendo timeA, timeB e opcionalmente placares e dataHora
     * @return 201 Created com os dados da partida criada
     */
    @POST
    @Operation(summary = "Criar novo jogo", description = "Cadastra uma nova partida com placar inicial e status EM_ANDAMENTO")
    @APIResponse(responseCode = "201", description = "Partida criada com sucesso")
    @APIResponse(responseCode = "400", description = "Dados da partida inválidos")
    public Response criar(@Valid CriarPartidaRequest req) {
        PartidaResponse r = service.criar(req, ORIGEM_API);
        return Response.status(Response.Status.CREATED).entity(r).build();
    }

    /**
     * GET /jogos
     * Lista as partidas com filtros opcionais de status, nome do time e paginação.
     * 
     * @param status Filtro por status (EM_ANDAMENTO, ENCERRADO)
     * @param time   Filtro por nome de time (busca parcial)
     * @param page   Índice da página (iniciando em 0)
     * @param size   Quantidade de itens por página
     * @return 200 OK com lista de partidas
     */
    @GET
    @Operation(summary = "Listar jogos", description = "Lista partidas cadastradas com filtros opcionais por status e time, e paginação")
    @APIResponse(responseCode = "200", description = "Lista de partidas")
    @APIResponse(responseCode = "400", description = "Parâmetros inválidos")
    public Response listar(
            @Parameter(description = "Filtrar por status (EM_ANDAMENTO, ENCERRADO)") @QueryParam("status") String status,
            @Parameter(description = "Filtrar por nome de time (busca parcial)") @QueryParam("time") String time,
            @Parameter(description = "Número da página (inicia em 0)") @QueryParam("page") String page,
            @Parameter(description = "Tamanho da página") @QueryParam("size") String size) {

        int p = converterParametroInteiro("page", page, 0);
        Integer s = size != null && !size.isBlank() ? converterParametroInteiro("size", size, 20) : null;

        List<PartidaResponse> lista = service.listar(status, time, p, s);
        return Response.ok(lista).build();
    }

    /**
     * GET /jogos/{id}
     * Busca os dados de uma partida específica por seu identificador único.
     * 
     * @param id Identificador da partida
     * @return 200 OK com a partida, ou 404 se não for encontrada
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar jogo por ID", description = "Retorna os detalhes de uma partida específica")
    @APIResponse(responseCode = "200", description = "Partida encontrada")
    @APIResponse(responseCode = "404", description = "Partida não encontrada")
    public Response buscarPorId(@PathParam("id") Long id) {
        PartidaResponse r = service.buscarPorId(id);
        return Response.ok(r).build();
    }

    /**
     * PUT /jogos/{id}/placar
     * Atualiza o placar de uma partida em andamento e dispara evento no RabbitMQ.
     * 
     * @param id  Identificador da partida
     * @param req DTO contendo os novos valores de placarA e placarB
     * @return 200 OK com a partida atualizada
     */
    @PUT
    @Path("/{id}/placar")
    @Operation(summary = "Atualizar placar", description = "Atualiza o placar de uma partida em andamento e publica evento no RabbitMQ")
    @APIResponse(responseCode = "200", description = "Placar atualizado com sucesso")
    @APIResponse(responseCode = "400", description = "Placar inválido")
    @APIResponse(responseCode = "404", description = "Partida não encontrada")
    @APIResponse(responseCode = "422", description = "Partida encerrada não permite alteração de placar")
    public Response atualizarPlacar(@PathParam("id") Long id, @Valid AtualizarPlacarRequest req) {
        PartidaResponse r = service.atualizarPlacar(id, req.getPlacarA(), req.getPlacarB(), ORIGEM_API);
        return Response.ok(r).build();
    }

    /**
     * PUT /jogos/{id}/status
     * Altera o status da partida (ex: encerrar jogo).
     * 
     * @param id  Identificador da partida
     * @param req DTO contendo o novo status desejado
     * @return 200 OK com o status atualizado
     */
    @PUT
    @Path("/{id}/status")
    @Operation(summary = "Alterar status do jogo", description = "Altera o status da partida (ex: encerrar jogo)")
    @APIResponse(responseCode = "200", description = "Status alterado com sucesso")
    @APIResponse(responseCode = "400", description = "Status inválido")
    @APIResponse(responseCode = "404", description = "Partida não encontrada")
    public Response atualizarStatus(@PathParam("id") Long id, @Valid AtualizarStatusRequest req) {
        PartidaResponse r = service.atualizarStatus(id, req.getStatus(), ORIGEM_API);
        return Response.ok(r).build();
    }

    /**
     * DELETE /jogos/{id}
     * Exclui uma partida do banco de dados e limpa o cache Redis.
     * Operação exclusiva da API REST (sem botão na interface do usuário).
     * 
     * @param id Identificador da partida a excluir
     * @return 204 No Content
     */
    @DELETE
    @Path("/{id}")
    @Operation(summary = "Excluir partida", description = "Remove uma partida cadastrada do banco de dados e do cache")
    @APIResponse(responseCode = "204", description = "Partida excluída com sucesso")
    @APIResponse(responseCode = "404", description = "Partida não encontrada")
    public Response excluir(@Parameter(description = "ID da partida a ser excluída") @PathParam("id") Long id) {
        service.excluir(id, ORIGEM_API);
        return Response.noContent().build();
    }

    /**
     * Converte e valida parâmetros numéricos de paginação.
     */
    private int converterParametroInteiro(String nome, String valor, int padrao) {
        if (valor == null || valor.isBlank()) return padrao;
        try {
            int n = Integer.parseInt(valor.trim());
            if (n < 0) {
                throw new RegraNegocioException(400, "Parâmetro '" + nome + "' inválido: informe um número inteiro maior ou igual a zero");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new RegraNegocioException(400, "Parâmetro '" + nome + "' inválido: informe um número inteiro maior ou igual a zero");
        }
    }
}