package br.com.scoreboard.service;

import br.com.scoreboard.cache.RedisCacheService;
import br.com.scoreboard.domain.Partida;
import br.com.scoreboard.domain.StatusPartida;
import br.com.scoreboard.domain.TipoAcao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.scoreboard.dto.CriarPartidaRequest;
import br.com.scoreboard.dto.PartidaResponse;
import br.com.scoreboard.dto.ScoreEvent;
import br.com.scoreboard.exception.PartidaNaoEncontradaException;
import br.com.scoreboard.exception.RegraNegocioException;
import br.com.scoreboard.messaging.ScoreEventPublisher;
import br.com.scoreboard.repository.PartidaRepository;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * CLASSE: PartidaService
 * PACOTE: br.com.scoreboard.service
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Centraliza todas as regras de negócio do Desafio Técnico de Gerenciamento
 * de Partidas de Futebol e Placares em Tempo Real.
 * 
 * PRINCIPAIS REGRAS DE NEGÓCIO APLICADAS:
 * 1. Não permitir times com nomes iguais em uma mesma partida (HTTP 400).
 * 2. Placares nunca podem ser negativos (HTTP 400).
 * 3. Não permitir alteração de placar em partidas encerradas (HTTP 422).
 * 4. Toda alteração de placar ou status dispara evento assíncrono para o RabbitMQ
 *    para manter o Redis atualizado para visualização em tempo real.
 * 5. Registro automático de auditoria para todas as operações críticas (Criação,
 *    Atualização de Placar, Mudança de Status e Exclusão). O campo 'usuario' guarda
 *    a origem: 'api-rest' ou 'painel-web'.
 */
@Stateless
public class PartidaService {

    @Inject
    PartidaRepository repo;

    @Inject
    ScoreEventPublisher publisher;

    @Inject
    RedisCacheService redis;

    @Inject
    AuditoriaService auditoria;

    @Inject
    ObjectMapper mapper;

    @Inject
    @ConfigProperty(name = "JOGOS_PAGE_SIZE_DEFAULT", defaultValue = "20")
    int pageSizeDefault;

    @Inject
    @ConfigProperty(name = "JOGOS_PAGE_SIZE_MAX", defaultValue = "100")
    int pageSizeMax;

    /**
     * Cadastra uma nova partida no sistema.
     * 
     * REGRAS VALIDADAS:
     * - O time mandante e o time visitante não podem ser iguais.
     * - Placares iniciais não podem ser negativos.
     * - Salva no PostgreSQL e publica evento para aquecer o cache Redis.
     * 
     * @param req     DTO com os dados da nova partida
     * @param usuario Nome do usuário logado ou "painel-web" (origem da operação)
     * @return PartidaResponse com os dados salvos e ID gerado
     */
    public PartidaResponse criar(CriarPartidaRequest req, String usuario) {
        // Validação 1: Times iguais
        if (req.getTimeA() != null && req.getTimeA().equalsIgnoreCase(req.getTimeB())) {
            throw new RegraNegocioException(400, "Os times da partida não podem ser iguais");
        }

        // Validação 2: Placares negativos
        if ((req.getPlacarA() != null && req.getPlacarA() < 0) || (req.getPlacarB() != null && req.getPlacarB() < 0)) {
            throw new RegraNegocioException(400, "Os placares não podem ser negativos");
        }

        // Prepara entidade e persiste no PostgreSQL
        LocalDateTime data = req.getDataHoraPartida() != null ? req.getDataHoraPartida() : LocalDateTime.now();
        Partida p = new Partida(req.getTimeA(), req.getTimeB(), data);
        if (req.getPlacarA() != null) p.setPlacarA(req.getPlacarA());
        if (req.getPlacarB() != null) p.setPlacarB(req.getPlacarB());

        repo.salvar(p);

        // Registra evento na tabela de auditoria
        try {
            auditoria.registrar(TipoAcao.CRIACAO_PARTIDA, usuario, p.getId(), null, mapper.writeValueAsString(p));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao serializar para auditoria", e);
        }

        // Publica evento no RabbitMQ para inicializar o cache Redis
        publisher.publicar(ScoreEvent.de(p));

        return PartidaResponse.de(p);
    }

    /**
     * Atualiza o placar de uma partida em andamento.
     * 
     * REGRAS VALIDADAS (CENÁRIOS BDD):
     * - Placares não podem ser negativos (HTTP 400).
     * - Partida precisa existir (HTTP 404).
     * - Jogo encerrado NÃO permite alteração de placar (HTTP 422 - Cenário 2 BDD).
     * - Atualiza banco e publica no RabbitMQ (Cenário 1 BDD).
     * 
     * @param id      ID da partida
     * @param placarA Novo placar do time A
     * @param placarB Novo placar do time B
     * @param usuario Nome do usuário (origem da operação)
     * @return PartidaResponse atualizada
     */
    public PartidaResponse atualizarPlacar(Long id, int placarA, int placarB, String usuario) {
        if (placarA < 0 || placarB < 0) {
            throw new RegraNegocioException(400, "Os placares não podem ser negativos");
        }

        Partida p = carregar(id);

        if (p.getStatus() == StatusPartida.ENCERRADO) {
            throw new RegraNegocioException(422, "Não é permitido alterar o placar de uma partida encerrada");
        }

        String antes = p.getPlacarA() + "x" + p.getPlacarB();

        p.setPlacarA(placarA);
        p.setPlacarB(placarB);
        repo.salvar(p);

        // Dispara mensagem assíncrona no RabbitMQ -> Consumidor atualiza Redis em sub-milissegundos
        publisher.publicar(ScoreEvent.de(p));

        String depois = placarA + "x" + placarB;
        auditoria.registrar(TipoAcao.ATUALIZACAO_PLACAR, usuario, p.getId(), antes, depois);

        return PartidaResponse.de(p);
    }

    /**
     * Altera o status da partida (ex: de EM_ANDAMENTO para ENCERRADO).
     * 
     * @param id         ID da partida
     * @param novoStatus Novo StatusPartida (EM_ANDAMENTO, ENCERRADO)
     * @param usuario    Nome do usuário (origem da operação)
     * @return PartidaResponse com o novo status
     */
    public PartidaResponse atualizarStatus(Long id, StatusPartida novoStatus, String usuario) {
        if (novoStatus == null) {
            throw new RegraNegocioException(400, "O status não pode ser nulo");
        }

        Partida p = carregar(id);
        String antes = p.getStatus().name();
        p.setStatus(novoStatus);
        repo.salvar(p);

        // Notifica consumidores sobre a mudança de status
        publisher.publicar(ScoreEvent.de(p));

        auditoria.registrar(TipoAcao.ALTERACAO_STATUS, usuario, p.getId(), antes, novoStatus.name());

        return PartidaResponse.de(p);
    }

    /**
     * Exclui uma partida do banco de dados e do cache Redis.
     * Operação exclusiva via API REST (conforme solicitado pelo usuário, sem botão na tela).
     * 
     * @param id      ID da partida a excluir
     * @param usuario Nome do usuário (origem da operação)
     */
    public void excluir(Long id, String usuario) {
        Partida p = carregar(id);

        String dadosAntes = p.getTimeA() + " " + p.getPlacarA() + "x" + p.getPlacarB() + " " + p.getTimeB() + " (" + p.getStatus() + ")";

        // Remove do banco relacional
        repo.excluir(p);

        // Registra histórico na tabela de auditoria
        auditoria.registrar(TipoAcao.EXCLUSAO_PARTIDA, usuario, id, dadosAntes, "EXCLUIDO");

        // Limpa a chave do Redis
        if (redis != null) {
            redis.removerPlacar(id);
        }
    }

    /**
     * Lista as partidas cadastradas com suporte a filtros opcionais e paginação.
     * 
     * @param status Filtro opcional por status (EM_ANDAMENTO, ENCERRADO)
     * @param time   Filtro opcional por nome de time (busca parcial case-insensitive)
     * @param page   Número da página (0-indexed)
     * @param size   Quantidade de registros por página
     * @return Lista paginada de partidas
     */
    public List<PartidaResponse> listar(String status, String time, int page, Integer size) {
        StatusPartida statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = StatusPartida.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RegraNegocioException(400, "Status inválido: " + status);
            }
        }

        int limite = (size != null && size > 0) ? Math.min(size, pageSizeMax) : pageSizeDefault;
        int p = Math.max(0, page);
        return repo.listar(statusEnum, time, p, limite)
                .stream().map(PartidaResponse::de).collect(Collectors.toList());
    }

    /**
     * Busca os dados de uma partida pelo seu ID único.
     * 
     * @param id ID da partida
     * @return PartidaResponse correspondente
     * @throws PartidaNaoEncontradaException se o ID não existir
     */
    public PartidaResponse buscarPorId(Long id) {
        return PartidaResponse.de(carregar(id));
    }

    /**
     * Método auxiliar privado para carregar entidade Partida ou lançar 404 Not Found.
     */
    private Partida carregar(Long id) {
        return repo.buscarPorId(id)
                .orElseThrow(() -> new PartidaNaoEncontradaException("Partida com id " + id + " não encontrada"));
    }
}
