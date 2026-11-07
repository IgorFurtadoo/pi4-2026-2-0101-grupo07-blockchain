-- Esquema do Banco do Blockchain Service.
-- Montado em /docker-entrypoint-initdb.d: o MySQL só executa este arquivo na
-- primeira criação do volume. Para recriar: docker compose down -v.
-- Colunas conforme CT5 (wallet) e CT11 (task); seção 5 do desenho.
-- Endereços em minúsculo; datas em UTC.

CREATE TABLE wallet (
  ethereum_address      VARCHAR(42)  NOT NULL PRIMARY KEY,
  encrypted_private_key VARCHAR(255) NOT NULL,
  created_at            DATETIME(3)  NOT NULL
);

CREATE TABLE task (
  task_id             CHAR(36)       NOT NULL PRIMARY KEY,  -- UUID
  source_address      VARCHAR(42)    NOT NULL,
  destination_address VARCHAR(42)    NOT NULL,
  amount_eth          DECIMAL(38,18) NOT NULL,
  status              VARCHAR(16)    NOT NULL,              -- PENDING | CONFIRMED | FAILED
  transaction_hash    VARCHAR(66)    NULL,
  failure_reason      VARCHAR(32)    NULL,                  -- REJECTED_BY_NETWORK | TRANSACTION_REVERTED | INTERNAL_ERROR
  created_at          DATETIME(3)    NOT NULL,
  updated_at          DATETIME(3)    NOT NULL,
  INDEX idx_task_status_created (status, created_at),
  INDEX idx_task_source (source_address)
);
