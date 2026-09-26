package br.com.scoreboard.repository;

import br.com.scoreboard.domain.RegistroAuditoria;
import br.com.scoreboard.domain.TipoAcao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class AuditoriaRepository {

    @PersistenceContext(unitName = "scoreboardPU")
    EntityManager em;

    public RegistroAuditoria salvar(RegistroAuditoria r) { return em.merge(r); }

    public List<RegistroAuditoria> consultar(String usuario, String tipoAcao, String dataInicio, String dataFim) {
        StringBuilder jpql = new StringBuilder("SELECT r FROM RegistroAuditoria r WHERE 1=1");
        if (usuario != null)  jpql.append(" AND r.usuario = :usuario");
        if (tipoAcao != null) jpql.append(" AND r.tipoAcao = :tipoAcao");
        if (dataInicio != null) jpql.append(" AND r.timestamp >= :ini");
        if (dataFim != null)    jpql.append(" AND r.timestamp <= :fim");
        jpql.append(" ORDER BY r.timestamp DESC");

        TypedQuery<RegistroAuditoria> q = em.createQuery(jpql.toString(), RegistroAuditoria.class);
        if (usuario != null)  q.setParameter("usuario", usuario);
        if (tipoAcao != null) q.setParameter("tipoAcao", TipoAcao.valueOf(tipoAcao));
        if (dataInicio != null) q.setParameter("ini", LocalDateTime.parse(dataInicio));
        if (dataFim != null)    q.setParameter("fim", LocalDateTime.parse(dataFim));
        return q.getResultList();
    }
}
