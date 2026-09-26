package br.com.scoreboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * DTO DE ENTRADA: CriarPartidaRequest
 * PACOTE: br.com.scoreboard.dto
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Recebe e valida os dados de entrada para o endpoint POST /jogos.
 * Utiliza anotações do Jakarta Bean Validation (@NotBlank, @Size).
 */
public class CriarPartidaRequest {

    @NotBlank(message = "O nome do time mandante (timeA) é obrigatório")
    @Size(max = 100, message = "O nome do time mandante não pode ultrapassar 100 caracteres")
    private String timeA;

    @NotBlank(message = "O nome do time visitante (timeB) é obrigatório")
    @Size(max = 100, message = "O nome do time visitante não pode ultrapassar 100 caracteres")
    private String timeB;

    private Integer placarA = 0;
    private Integer placarB = 0;

    private LocalDateTime dataHoraPartida;

    public CriarPartidaRequest() {}

    public CriarPartidaRequest(String timeA, String timeB, LocalDateTime dataHoraPartida) {
        this.timeA = timeA;
        this.timeB = timeB;
        this.dataHoraPartida = dataHoraPartida;
    }

    public CriarPartidaRequest(String timeA, String timeB, Integer placarA, Integer placarB, LocalDateTime dataHoraPartida) {
        this.timeA = timeA;
        this.timeB = timeB;
        this.placarA = placarA != null ? placarA : 0;
        this.placarB = placarB != null ? placarB : 0;
        this.dataHoraPartida = dataHoraPartida;
    }

    // Getters e Setters
    public String getTimeA() { return timeA; }
    public void setTimeA(String timeA) { this.timeA = timeA; }

    public String getTimeB() { return timeB; }
    public void setTimeB(String timeB) { this.timeB = timeB; }

    public Integer getPlacarA() { return placarA != null ? placarA : 0; }
    public void setPlacarA(Integer placarA) { this.placarA = placarA != null ? placarA : 0; }

    public Integer getPlacarB() { return placarB != null ? placarB : 0; }
    public void setPlacarB(Integer placarB) { this.placarB = placarB != null ? placarB : 0; }

    public LocalDateTime getDataHoraPartida() { return dataHoraPartida; }
    public void setDataHoraPartida(LocalDateTime dataHoraPartida) { this.dataHoraPartida = dataHoraPartida; }
}