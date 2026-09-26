CREATE TABLE usuarios (
    id         BIGSERIAL    PRIMARY KEY,
    username   VARCHAR(100) NOT NULL,
    senha_hash VARCHAR(100) NOT NULL,
    role       VARCHAR(10)  NOT NULL CHECK (role IN ('ADMIN','VIEWER')),
    CONSTRAINT uk_usuarios_username UNIQUE (username)
);