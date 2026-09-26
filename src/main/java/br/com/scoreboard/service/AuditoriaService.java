package br.com.scoreboard.service;

import br.com.scoreboard.domain.RegistroAuditoria;
import br.com.scoreboard.domain.TipoAcao;
import br.com.scoreboard.dto.AuditoriaResponse;
import br.com.scoreboard.repository.AuditoriaRepository;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================================================
 * SERVIÇO: AuditoriaService
 * PACOTE: br.com.scoreboard.service
 * ============================================================================
 *
 * RESPONSABILIDADE:
 * Grava o histórico de todas as operações realizadas nas partidas (criação,
 * gols, mudanças de status e exclusão) para rastreabilidade e governança.
 * O campo 'usuario' armazena a origem da operação ('painel-web' ou 'api-rest').
 */
@Stateless
public class AuditoriaService {

    @Inject
    private AuditoriaRepository repo;

    /**
     * Registra uma alteração de partida na trilha de auditoria.
     *
     * @param tipo       Tipo da ação (CRIACAO_PARTIDA, ATUALIZACAO_PLACAR, etc.)
     * @param origem     Identificador da origem ('painel-web' ou 'api-rest')
     * @param idEntidade ID da partida afetada
     * @param antes      Estado anterior aos dados
     * @param depois     Novo estado após a alteração
     */
    public void registrar(TipoAcao tipo, String origem, Long idEntidade, String antes, String depois) {
        RegistroAuditoria r = new RegistroAuditoria();
        r.setUsuario(origem);
        r.setTipoAcao(tipo);
        r.setEntidadeAfetada("Partida");
        r.setIdEntidade(idEntidade);
        r.setValorAntes(antes);
        r.setValorDepois(depois);
        r.setTimestamp(LocalDateTime.now());
        repo.salvar(r);
    }

    /**
     * Consulta o histórico de auditoria com filtros opcionais.
     */
    public List<AuditoriaResponse> consultar(String usuario, String tipoAcao, String dataInicio, String dataFim) {
        return repo.consultar(usuario, tipoAcao, dataInicio, dataFim)
                .stream().map(AuditoriaResponse::de).collect(Collectors.toList());
    }
}
