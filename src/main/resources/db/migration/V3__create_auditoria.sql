CREATE TABLE auditoria (
    id               BIGSERIAL    PRIMARY KEY,
    usuario          VARCHAR(100) NOT NULL,
    tipo_acao        VARCHAR(30)  NOT NULL,
    entidade_afetada VARCHAR(50),
    id_entidade      BIGINT,
    valor_antes      TEXT,
    valor_depois     TEXT,
    timestamp        TIMESTAMP    NOT NULL
);

CREATE INDEX idx_auditoria_usuario   ON auditoria (usuario);
CREATE INDEX idx_auditoria_tipo_acao ON auditoria (tipo_acao);
CREATE INDEX idx_auditoria_timestamp ON auditoria (timestamp);