package br.com.scoreboard.rest;

import br.com.scoreboard.domain.StatusPartida;
import br.com.scoreboard.dto.AtualizarPlacarRequest;
import br.com.scoreboard.dto.AtualizarStatusRequest;
import br.com.scoreboard.dto.CriarPartidaRequest;
import br.com.scoreboard.dto.PartidaResponse;
import br.com.scoreboard.exception.RegraNegocioException;
import br.com.scoreboard.service.PartidaService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * TESTES UNITÁRIOS DA CAMADA REST: JogosResourceTest
 * PACOTE: br.com.scoreboard.rest
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Testa o comportamento e os status HTTP retornados pelos endpoints de /jogos.
 */
@ExtendWith(MockitoExtension.class)
class JogosResourceTest {

    @Mock
    private PartidaService service;

    @InjectMocks
    private JogosResource resource;

    @Test
    @DisplayName("POST /jogos: Deve retornar 201 Created ao cadastrar partida com origem 'api-rest'")
    void deveCriarPartidaRetornando201() {
        CriarPartidaRequest req = new CriarPartidaRequest("Flamengo", "Palmeiras", LocalDateTime.now());
        PartidaResponse mockResponse = mock(PartidaResponse.class);
        when(service.criar(eq(req), eq("api-rest"))).thenReturn(mockResponse);

        Response response = resource.criar(req);

        assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(mockResponse);
        verify(service).criar(eq(req), eq("api-rest"));
    }

    @Test
    @DisplayName("GET /jogos: Deve retornar 200 OK com a lista de partidas")
    void deveListarJogosRetornando200() {
        List<PartidaResponse> mockLista = List.of(mock(PartidaResponse.class));
        when(service.listar(any(), any(), anyInt(), any())).thenReturn(mockLista);

        Response response = resource.listar("EM_ANDAMENTO", "Fla", "0", "10");

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(mockLista);
        verify(service).listar(eq("EM_ANDAMENTO"), eq("Fla"), eq(0), eq(10));
    }

    @Test
    @DisplayName("GET /jogos: Deve lançar RegraNegocioException 400 se paginação for negativa")
    void deveFalharListarComPaginaInvalida() {
        assertThatThrownBy(() -> resource.listar(null, null, "-1", "10"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Parâmetro 'page' inválido");

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("GET /jogos/{id}: Deve retornar 200 OK ao encontrar partida por ID")
    void deveBuscarPorIdRetornando200() {
        PartidaResponse mockResponse = mock(PartidaResponse.class);
        when(service.buscarPorId(1L)).thenReturn(mockResponse);

        Response response = resource.buscarPorId(1L);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(mockResponse);
    }

    @Test
    @DisplayName("PUT /jogos/{id}/placar: Deve retornar 200 OK ao atualizar placar com origem 'api-rest'")
    void deveAtualizarPlacarRetornando200() {
        AtualizarPlacarRequest req = new AtualizarPlacarRequest();
        req.setPlacarA(2);
        req.setPlacarB(1);
        PartidaResponse mockResponse = mock(PartidaResponse.class);
        when(service.atualizarPlacar(eq(1L), eq(2), eq(1), eq("api-rest"))).thenReturn(mockResponse);

        Response response = resource.atualizarPlacar(1L, req);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(mockResponse);
        verify(service).atualizarPlacar(eq(1L), eq(2), eq(1), eq("api-rest"));
    }

    @Test
    @DisplayName("PUT /jogos/{id}/status: Deve retornar 200 OK ao alterar status com origem 'api-rest'")
    void deveAtualizarStatusRetornando200() {
        AtualizarStatusRequest req = new AtualizarStatusRequest();
        req.setStatus(StatusPartida.ENCERRADO);
        PartidaResponse mockResponse = mock(PartidaResponse.class);
        when(service.atualizarStatus(eq(1L), eq(StatusPartida.ENCERRADO), eq("api-rest"))).thenReturn(mockResponse);

        Response response = resource.atualizarStatus(1L, req);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(mockResponse);
        verify(service).atualizarStatus(eq(1L), eq(StatusPartida.ENCERRADO), eq("api-rest"));
    }

    @Test
    @DisplayName("DELETE /jogos/{id}: Deve retornar 204 No Content ao excluir partida com origem 'api-rest'")
    void deveExcluirPartidaRetornando204() {
        doNothing().when(service).excluir(eq(1L), eq("api-rest"));

        Response response = resource.excluir(1L);

        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        verify(service).excluir(eq(1L), eq("api-rest"));
    }
}
