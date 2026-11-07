#!/usr/bin/env bash
# Smoke test do POST /wallets (CT4) contra o compose de desenvolvimento.
#
# Uso: scripts/smoke-wallet.sh  (com "docker compose up -d" já rodando)
#
# A SERVICE_KEY vem da variável exportada ou, se não houver, do ambiente do
# container. Ela nunca é impressa: vai para o curl pela entrada padrão (-H @-),
# para não aparecer na tela nem na lista de processos. Não use "bash -x" aqui.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8081}"
cd "$(dirname "$0")/.."

if [ -z "${SERVICE_KEY:-}" ]; then
  SERVICE_KEY="$(docker compose exec -T blockchain-service printenv SERVICE_KEY)"
fi

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT
FAILURES=0

# post_wallets <rótulo> <status esperado> <regex do corpo> <cabeçalho X-Service-Key ou vazio>
post_wallets() {
  local label="$1" expected_status="$2" body_regex="$3" header="$4"
  local status
  status="$(printf '%s\n' "$header" | curl -s -o "$BODY_FILE" -w '%{http_code}' \
    -X POST -H @- "$BASE_URL/wallets")"
  local body
  body="$(cat "$BODY_FILE")"

  if [ "$status" = "$expected_status" ] && [[ "$body" =~ $body_regex ]]; then
    echo "OK    $label -> $status $body"
  else
    echo "FALHA $label -> $status $body (esperado $expected_status)"
    FAILURES=$((FAILURES + 1))
  fi
}

ERROR_401='^\{"code":"SERVICE_KEY_INVALID","message":"[^"]+"\}$'
CREATED_201='^\{"ethereumAddress":"0x[0-9a-fA-F]{40}"\}$'

post_wallets "sem chave"     401 "$ERROR_401"   ""
post_wallets "chave errada"  401 "$ERROR_401"   "X-Service-Key: chave-errada-de-teste"
post_wallets "chave certa"   201 "$CREATED_201" "X-Service-Key: $SERVICE_KEY"

if [ "$FAILURES" -gt 0 ]; then
  echo "$FAILURES verificação(ões) falharam."
  exit 1
fi
echo "Todas as verificações passaram."
