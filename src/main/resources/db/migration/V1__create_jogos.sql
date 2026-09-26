CREATE TABLE jogos (
    id                BIGSERIAL    PRIMARY KEY,
    time_a            VARCHAR(100) NOT NULL,
    time_b            VARCHAR(100) NOT NULL,
    placar_a          INT          NOT NULL DEFAULT 0 CHECK (placar_a >= 0),
    placar_b          INT          NOT NULL DEFAULT 0 CHECK (placar_b >= 0),
    status            VARCHAR(20)  NOT NULL DEFAULT 'EM_ANDAMENTO',
    data_hora_partida TIMESTAMP    NOT NULL
);

CREATE INDEX idx_jogos_status ON jogos (status);
CREATE INDEX idx_jogos_time_a ON jogos (time_a);
CREATE INDEX idx_jogos_time_b ON jogos (time_b);