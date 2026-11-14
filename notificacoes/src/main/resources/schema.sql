CREATE TABLE IF NOT EXISTS notificacao (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    tentativas INT NOT NULL,
    payload_json CLOB,
    ultimo_erro VARCHAR(2000),
    proxima_tentativa_ms BIGINT,
    criado_em_ms BIGINT NOT NULL,
    atualizado_em_ms BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notificacao_retentativa
    ON notificacao (status, proxima_tentativa_ms);
