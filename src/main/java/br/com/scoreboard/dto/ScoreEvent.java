package br.com.scoreboard.dto;

import br.com.scoreboard.domain.Partida;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * ============================================================================
 * DTO DE EVENTO: ScoreEvent
 * PACOTE: br.com.scoreboard.dto
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Objeto de transferência de dados que representa o evento publicado na fila
 * do RabbitMQ sempre que um gol é marcado ou o status da partida é alterado.
 * 
 * CAMPOS ENVIADOS NO EVENTO JSON:
 * - id: Identificador da partida
 * - timeA: Nome do time mandante
 * - timeB: Nome do time visitante
 * - placarA: Placar atualizado do mandante
 * - placarB: Placar atualizado do visitante
 * - status: Status atual (EM_ANDAMENTO ou ENCERRADO)
 * - timestamp: Momento exato em que o evento foi disparado (ISO-8601 UTC)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreEvent {

    private Long id;
    private String timeA;
    private String timeB;
    private int placarA;
    private int placarB;
    private String status;
    private String timestamp;

    public ScoreEvent() {}

    /**
     * Factory method para converter uma entidade Partida em um evento de placar pronto para a fila.
     * 
     * @param p Entidade da partida com os dados atualizados
     * @return ScoreEvent populado com timestamp atual
     */
    public static ScoreEvent de(Partida p) {
        ScoreEvent e = new ScoreEvent();
        e.id = p.getId();
        e.timeA = p.getTimeA();
        e.timeB = p.getTimeB();
        e.placarA = p.getPlacarA();
        e.placarB = p.getPlacarB();
        e.status = p.getStatus().name();
        e.timestamp = Instant.now().toString();
        return e;
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTimeA() { return timeA; }
    public void setTimeA(String timeA) { this.timeA = timeA; }

    public String getTimeB() { return timeB; }
    public void setTimeB(String timeB) { this.timeB = timeB; }

    public int getPlacarA() { return placarA; }
    public void setPlacarA(int placarA) { this.placarA = placarA; }

    public int getPlacarB() { return placarB; }
    public void setPlacarB(int placarB) { this.placarB = placarB; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}