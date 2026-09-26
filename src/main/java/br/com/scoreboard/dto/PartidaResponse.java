package br.com.scoreboard.dto;

import br.com.scoreboard.domain.Partida;

/**
 * ============================================================================
 * DTO DE SAÍDA: PartidaResponse
 * PACOTE: br.com.scoreboard.dto
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Objeto de resposta JSON retornado pelos endpoints da API REST (/jogos).
 * Isola a entidade JPA do contrato externo da API, evitando vazamento de dados internos.
 */
public class PartidaResponse {

    private Long id;
    private String timeA;
    private String timeB;
    private int placarA;
    private int placarB;
    private String status;
    private String dataHoraPartida;

    /**
     * Converte a entidade relacional Partida para o DTO de resposta JSON.
     * 
     * @param p Entidade carregada do banco de dados
     * @return PartidaResponse formatado
     */
    public static PartidaResponse de(Partida p) {
        PartidaResponse r = new PartidaResponse();
        r.id = p.getId();
        r.timeA = p.getTimeA();
        r.timeB = p.getTimeB();
        r.placarA = p.getPlacarA();
        r.placarB = p.getPlacarB();
        r.status = p.getStatus() != null ? p.getStatus().name() : null;
        r.dataHoraPartida = p.getDataHoraPartida() != null ? p.getDataHoraPartida().toString() : null;
        return r;
    }

    public Long getId() { return id; }
    public String getTimeA() { return timeA; }
    public String getTimeB() { return timeB; }
    public int getPlacarA() { return placarA; }
    public int getPlacarB() { return placarB; }
    public String getStatus() { return status; }
    public String getDataHoraPartida() { return dataHoraPartida; }
}