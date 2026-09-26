package br.com.scoreboard.dto;

import jakarta.validation.constraints.Min;

/**
 * ============================================================================
 * DTO DE ENTRADA: AtualizarPlacarRequest
 * PACOTE: br.com.scoreboard.dto
 * ============================================================================
 * 
 * RESPONSABILIDADE:
 * Recebe e valida os novos placares para o endpoint PUT /jogos/{id}/placar.
 * A anotação @Min(0) garante que nenhum placar negativo seja aceito (HTTP 400).
 */
public class AtualizarPlacarRequest {

    @Min(value = 0, message = "O placar do time A não pode ser negativo")
    private int placarA;

    @Min(value = 0, message = "O placar do time B não pode ser negativo")
    private int placarB;

    public int getPlacarA() { return placarA; }
    public void setPlacarA(int placarA) { this.placarA = placarA; }

    public int getPlacarB() { return placarB; }
    public void setPlacarB(int placarB) { this.placarB = placarB; }
}