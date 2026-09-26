package br.com.scoreboard.repository;

import br.com.scoreboard.domain.Partida;
import br.com.scoreboard.domain.StatusPartida;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * REPOSITÓRIO: PartidaRepository
 * PACOTE: br.com.scoreboard.repository
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Camada de acesso a dados (Data Access Object / Repository) para a entidade Partida.
 * Utiliza o EntityManager gerenciado pelo Jakarta Persistence (JPA).
 * 
 * OPERAÇÕES:
 * - salvar: persiste nova partida ou atualiza existente (merge).
 * - excluir: remove partida do banco de dados relacional.
 * - buscarPorId: busca pela chave primária (find).
 * - listar: executa consulta JPQL dinâmica com filtros por status, time e paginação.
 * - pingOk: executa 'SELECT 1' nativo para verificar saúde do banco PostgreSQL.
 */
@ApplicationScoped
public class PartidaRepository {

    @PersistenceContext(unitName = "scoreboardPU")
    private EntityManager em;

    /**
     * Persiste ou atualiza uma partida no PostgreSQL.
     */
    public Partida salvar(Partida p) {
        return em.merge(p);
    }

    /**
     * Remove a partida da tabela de jogos.
     */
    public void excluir(Partida p) {
        em.remove(em.contains(p) ? p : em.merge(p));
    }

    /**
     * Busca uma partida pelo ID.
     */
    public Optional<Partida> buscarPorId(Long id) {
        return Optional.ofNullable(em.find(Partida.class, id));
    }

    /**
     * Lista partidas com filtros dinâmicos de status e busca textual por time,
     * aplicando controle de paginação (offset e limit).
     * 
     * @param status Status opcional (EM_ANDAMENTO, INTERVALO, ENCERRADO)
     * @param time   Nome parcial opcional de time para filtrar timeA ou timeB
     * @param page   Índice da página (inicia em 0)
     * @param size   Quantidade de registros por página
     * @return Lista de entidades Partida
     */
    public List<Partida> listar(StatusPartida status, String time, int page, int size) {
        StringBuilder jpql = new StringBuilder("SELECT p FROM Partida p WHERE 1=1");
        if (status != null) {
            jpql.append(" AND p.status = :status");
        }
        if (time != null && !time.isBlank()) {
            jpql.append(" AND (LOWER(p.timeA) LIKE :time OR LOWER(p.timeB) LIKE :time)");
        }
        jpql.append(" ORDER BY p.id");

        TypedQuery<Partida> query = em.createQuery(jpql.toString(), Partida.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (time != null && !time.isBlank()) {
            query.setParameter("time", "%" + time.toLowerCase() + "%");
        }
        
        // Paginação via JPA
        query.setFirstResult(page * size);
        query.setMaxResults(size);

        return query.getResultList();
    }

    /**
     * Executa query simples para validar conectividade com o banco (Health Check).
     */
    public boolean pingOk() {
        try {
            em.createNativeQuery("SELECT 1").getSingleResult();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}