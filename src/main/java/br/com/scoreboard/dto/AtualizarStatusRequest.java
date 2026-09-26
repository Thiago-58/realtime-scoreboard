package br.com.scoreboard.dto;

import br.com.scoreboard.domain.StatusPartida;
import jakarta.validation.constraints.NotNull;

/**
 * ============================================================================
 * DTO DE ENTRADA: AtualizarStatusRequest
 * PACOTE: br.com.scoreboard.dto
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Recebe e valida a transição de status para o endpoint PUT /jogos/{id}/status.
 * A anotação @NotNull garante que um status válido seja informado (HTTP 400).
 */
public class AtualizarStatusRequest {

    @NotNull(message = "O campo status é obrigatório (EM_ANDAMENTO ou ENCERRADO)")
    private StatusPartida status;

    public StatusPartida getStatus() { return status; }
    public void setStatus(StatusPartida status) { this.status = status; }
}