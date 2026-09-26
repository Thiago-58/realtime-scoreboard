package br.com.scoreboard.domain;

/**
 * ============================================================================
 * ENUM: TipoAcao
 * PACOTE: br.com.scoreboard.domain
 * ============================================================================
 *
 * RESPONSABILIDADE:
 * Tipifica as operações auditadas sobre as partidas no sistema.
 */
public enum TipoAcao {
    CRIACAO_PARTIDA,
    ATUALIZACAO_PLACAR,
    ALTERACAO_STATUS,
    EXCLUSAO_PARTIDA
}
