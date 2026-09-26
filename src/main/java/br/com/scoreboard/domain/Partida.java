package br.com.scoreboard.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * ENTIDADE: Partida
 * TABELA: jogos
 * PACOTE: br.com.scoreboard.domain
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Representa a entidade relacional de uma partida de futebol no banco de dados
 * PostgreSQL, mapeada via JPA / Hibernate / EclipseLink.
 * 
 * ATRIBUTOS MAPEADOS (CONFORME REQUISITO 2.1 DO DESAFIO):
 * - id: Identificador único gerado automaticamente pelo banco (BIGSERIAL).
 * - timeA: Nome do time mandante (obrigatório, máximo 100 caracteres).
 * - timeB: Nome do time visitante (obrigatório, máximo 100 caracteres).
 * - placarA: Gols marcados pelo time mandante (inicia em 0).
 * - placarB: Gols marcados pelo time visitante (inicia em 0).
 * - status: Estado atual da partida (EM_ANDAMENTO ou ENCERRADO).
 * - dataHoraPartida: Data e horário programados para a partida.
 */
@Entity
@Table(name = "jogos")
public class Partida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "time_a", nullable = false, length = 100)
    private String timeA;

    @Column(name = "time_b", nullable = false, length = 100)
    private String timeB;

    @Column(name = "placar_a", nullable = false)
    private int placarA = 0;

    @Column(name = "placar_b", nullable = false)
    private int placarB = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPartida status = StatusPartida.EM_ANDAMENTO;

    @Column(name = "data_hora_partida", nullable = false)
    private LocalDateTime dataHoraPartida;

    /**
     * Construtor padrão sem argumentos exigido pela especificação JPA.
     */
    public Partida() {}

    /**
     * Construtor de conveniência para inicializar nova partida com os times e data/hora.
     */
    public Partida(String timeA, String timeB, LocalDateTime dataHoraPartida) {
        this.timeA = timeA;
        this.timeB = timeB;
        this.dataHoraPartida = dataHoraPartida;
        this.placarA = 0;
        this.placarB = 0;
        this.status = StatusPartida.EM_ANDAMENTO;
    }

    // ========================================================================
    // GETTERS E SETTERS
    // ========================================================================

    public Long getId() {
        return id;
    }

    public String getTimeA() {
        return timeA;
    }

    public void setTimeA(String timeA) {
        this.timeA = timeA;
    }

    public String getTimeB() {
        return timeB;
    }

    public void setTimeB(String timeB) {
        this.timeB = timeB;
    }

    public int getPlacarA() {
        return placarA;
    }

    public void setPlacarA(int placarA) {
        this.placarA = placarA;
    }

    public int getPlacarB() {
        return placarB;
    }

    public void setPlacarB(int placarB) {
        this.placarB = placarB;
    }

    public StatusPartida getStatus() {
        return status;
    }

    public void setStatus(StatusPartida status) {
        this.status = status;
    }

    public LocalDateTime getDataHoraPartida() {
        return dataHoraPartida;
    }

    public void setDataHoraPartida(LocalDateTime dataHoraPartida) {
        this.dataHoraPartida = dataHoraPartida;
    }
}