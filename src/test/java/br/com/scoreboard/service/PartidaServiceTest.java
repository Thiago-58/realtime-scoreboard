package br.com.scoreboard.service;

import br.com.scoreboard.cache.RedisCacheService;
import br.com.scoreboard.domain.Partida;
import br.com.scoreboard.domain.StatusPartida;
import br.com.scoreboard.domain.TipoAcao;
import br.com.scoreboard.dto.CriarPartidaRequest;
import br.com.scoreboard.dto.PartidaResponse;
import br.com.scoreboard.dto.ScoreEvent;
import br.com.scoreboard.exception.PartidaNaoEncontradaException;
import br.com.scoreboard.exception.RegraNegocioException;
import br.com.scoreboard.messaging.ScoreEventPublisher;
import br.com.scoreboard.repository.PartidaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * CLASSE: PartidaServiceTest
 * PACOTE: br.com.scoreboard.service
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Testes unitários das regras de negócio do serviço de partidas (PartidaService).
 * Cobre cenários BDD 1 e 2, validações de placares negativos, bloqueio pós-encerramento
 * e limpeza de cache.
 */
@ExtendWith(MockitoExtension.class)
class PartidaServiceTest {

    @Mock
    private PartidaRepository repo;

    @Mock
    private ScoreEventPublisher publisher;

    @Mock
    private RedisCacheService redis;

    @Mock
    private AuditoriaService auditoria;

    @Mock
    private ObjectMapper mapper;

    @InjectMocks
    private PartidaService service;

    @BeforeEach
    void setUp() {
        service.pageSizeDefault = 20;
        service.pageSizeMax = 100;
    }

    @Test
    @DisplayName("Deve criar partida com placar inicial 0x0 e status EM_ANDAMENTO")
    void deveCriarPartidaComSucesso() throws Exception {
        CriarPartidaRequest req = new CriarPartidaRequest();
        req.setTimeA("Flamengo");
        req.setTimeB("Palmeiras");
        req.setDataHoraPartida(LocalDateTime.now());

        // O serializador é chamado por criar() ao registrar a auditoria
        when(mapper.writeValueAsString(any())).thenReturn("{}");

        PartidaResponse resp = service.criar(req, "admin");

        assertThat(resp).isNotNull();
        assertThat(resp.getTimeA()).isEqualTo("Flamengo");
        assertThat(resp.getTimeB()).isEqualTo("Palmeiras");
        assertThat(resp.getPlacarA()).isZero();
        assertThat(resp.getPlacarB()).isZero();
        assertThat(resp.getStatus()).isEqualTo("EM_ANDAMENTO");
        verify(repo).salvar(any(Partida.class));
        verify(publisher).publicar(any(ScoreEvent.class));
    }

    @Test
    @DisplayName("Deve rejeitar criação de partida com times iguais")
    void deveRejeitarTimesIguais() {
        CriarPartidaRequest req = new CriarPartidaRequest();
        req.setTimeA("Flamengo");
        req.setTimeB("flamengo");
        req.setDataHoraPartida(LocalDateTime.now());

        assertThatThrownBy(() -> service.criar(req, "admin"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não podem ser iguais");

        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("Deve rejeitar criação de partida com placar negativo")
    void deveRejeitarPlacarNegativoAoCriar() {
        CriarPartidaRequest req = new CriarPartidaRequest();
        req.setTimeA("Flamengo");
        req.setTimeB("Fluminense");
        req.setPlacarA(-1);
        req.setDataHoraPartida(LocalDateTime.now());

        assertThatThrownBy(() -> service.criar(req, "admin"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não podem ser negativos");

        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("Cenário 1 BDD: Deve atualizar placar de jogo em andamento e publicar evento no RabbitMQ")
    void deveAtualizarPlacarJogoEmAndamento() {
        Partida partida = new Partida("Flamengo", "Vasco", LocalDateTime.now());
        partida.setStatus(StatusPartida.EM_ANDAMENTO);
        when(repo.buscarPorId(1L)).thenReturn(Optional.of(partida));

        PartidaResponse resp = service.atualizarPlacar(1L, 2, 1, "admin");

        assertThat(resp.getPlacarA()).isEqualTo(2);
        assertThat(resp.getPlacarB()).isEqualTo(1);

        ArgumentCaptor<ScoreEvent> captor = ArgumentCaptor.forClass(ScoreEvent.class);
        verify(publisher).publicar(captor.capture());
        ScoreEvent evento = captor.getValue();
        assertThat(evento.getPlacarA()).isEqualTo(2);
        assertThat(evento.getPlacarB()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cenário 2 BDD: Deve impedir alteração de placar após encerramento")
    void deveRecusarAlteracaoPlacarJogoEncerrado() {
        Partida partida = new Partida("Corinthians", "São Paulo", LocalDateTime.now());
        partida.setStatus(StatusPartida.ENCERRADO);
        when(repo.buscarPorId(2L)).thenReturn(Optional.of(partida));

        assertThatThrownBy(() -> service.atualizarPlacar(2L, 1, 0, "admin"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Não é permitido alterar o placar de uma partida encerrada");

        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Deve rejeitar atualização de placar com valor negativo")
    void deveRejeitarPlacarNegativoAoAtualizar() {
        assertThatThrownBy(() -> service.atualizarPlacar(1L, -1, 0, "admin"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não podem ser negativos");

        verifyNoInteractions(repo);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Deve alterar status da partida (ex: encerrar jogo)")
    void deveEncerrarPartida() {
        Partida partida = new Partida("Grêmio", "Internacional", LocalDateTime.now());
        partida.setStatus(StatusPartida.EM_ANDAMENTO);
        when(repo.buscarPorId(3L)).thenReturn(Optional.of(partida));

        PartidaResponse resp = service.atualizarStatus(3L, StatusPartida.ENCERRADO, "admin");

        assertThat(resp.getStatus()).isEqualTo("ENCERRADO");
    }

    @Test
    @DisplayName("Deve lançar PartidaNaoEncontradaException para id inexistente")
    void deveLancarExceptionQuandoNaoEncontrado() {
        when(repo.buscarPorId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(999L))
                .isInstanceOf(PartidaNaoEncontradaException.class)
                .hasMessageContaining("999 não encontrada");
    }

    @Test
    @DisplayName("Deve excluir partida com sucesso, limpando Redis")
    void deveExcluirPartidaComSucesso() {
        Partida partida = new Partida("Santos", "São Paulo", LocalDateTime.now());
        when(repo.buscarPorId(5L)).thenReturn(Optional.of(partida));

        service.excluir(5L, "admin");

        verify(repo).excluir(partida);
        verify(auditoria).registrar(eq(TipoAcao.EXCLUSAO_PARTIDA), eq("admin"), eq(5L), anyString(), eq("EXCLUIDO"));
        verify(redis).removerPlacar(5L);
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar excluir partida com id inexistente")
    void deveLancarExceptionAoExcluirIdInexistente() {
        when(repo.buscarPorId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(999L, "admin"))
                .isInstanceOf(PartidaNaoEncontradaException.class)
                .hasMessageContaining("999 não encontrada");

        verify(repo, never()).excluir(any());
    }

    @Test
    @DisplayName("Deve rejeitar alteração de status com valor nulo")
    void deveRejeitarStatusNulo() {
        assertThatThrownBy(() -> service.atualizarStatus(1L, null, "admin"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("O status não pode ser nulo");
    }
}