#!/usr/bin/env bash
# Smoke test do GET /wallets/{ethereumAddress}/balance (CT7) contra o compose de
# desenvolvimento, usando o Provedor RPC real configurado no .env.
#
# Uso: scripts/smoke-balance.sh  (com "docker compose up -d" já rodando)
#
# Com EXPECT_RPC_DOWN=1, o último caso espera 502 RPC_UNAVAILABLE em vez de 200.
# Serve para a segunda instância com RPC_URL apontando para uma porta fechada
# (veja a seção Comandos do CLAUDE.md), junto com BASE_URL=http://127.0.0.1:8083.
#
# A SERVICE_KEY vem da variável exportada ou, se não houver, do ambiente do
# container. Ela nunca é impressa: vai para o curl pela entrada padrão (-H @-),
# para não aparecer na tela nem na lista de processos. Não use "bash -x" aqui.
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8081}"
EXPECT_RPC_DOWN="${EXPECT_RPC_DOWN:-0}"
cd "$(dirname "$0")/.."

if [ -z "${SERVICE_KEY:-}" ]; then
  SERVICE_KEY="$(docker compose exec -T blockchain-service printenv SERVICE_KEY)"
fi

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT
FAILURES=0
BODY=""

# request <rótulo> <método> <caminho> <status esperado> <regex do corpo> <cabeçalho X-Service-Key ou vazio>
# Deixa o corpo da resposta em $BODY.
request() {
  local label="$1" method="$2" path="$3" expected_status="$4" body_regex="$5" header="$6"
  local status
  status="$(printf '%s\n' "$header" | curl -s -o "$BODY_FILE" -w '%{http_code}' \
    -X "$method" -H @- "$BASE_URL$path")"
  BODY="$(cat "$BODY_FILE")"

  if [ "$status" = "$expected_status" ] && [[ "$BODY" =~ $body_regex ]]; then
    echo "OK    $label -> $status $BODY"
  else
    echo "FALHA $label -> $status $BODY (esperado $expected_status)"
    FAILURES=$((FAILURES + 1))
  fi
}

KEY_HEADER="X-Service-Key: $SERVICE_KEY"
VALID_ADDRESS_WITHOUT_WALLET="0x0000000000000000000000000000000000000001"

error_regex() {
  printf '^\\{"code":"%s","message":"[^"]+"\\}$' "$1"
}

request "sem chave" GET "/wallets/$VALID_ADDRESS_WITHOUT_WALLET/balance" \
  401 "$(error_regex SERVICE_KEY_INVALID)" ""
request "endereço inválido" GET "/wallets/0x123/balance" \
  400 "$(error_regex ADDRESS_INVALID)" "$KEY_HEADER"
request "endereço sem carteira" GET "/wallets/$VALID_ADDRESS_WITHOUT_WALLET/balance" \
  404 "$(error_regex WALLET_NOT_FOUND)" "$KEY_HEADER"

request "criar carteira" POST "/wallets" \
  201 '^\{"ethereumAddress":"0x[0-9a-fA-F]{40}"\}$' "$KEY_HEADER"
ADDRESS="$(printf '%s' "$BODY" | sed -E 's/.*"ethereumAddress":"(0x[0-9a-fA-F]{40})".*/\1/')"

if [ "$EXPECT_RPC_DOWN" = "1" ]; then
  request "saldo com RPC fora" GET "/wallets/$ADDRESS/balance" \
    502 "$(error_regex RPC_UNAVAILABLE)" "$KEY_HEADER"
else
  request "saldo da carteira nova" GET "/wallets/$ADDRESS/balance" \
    200 "^\\{\"ethereumAddress\":\"$ADDRESS\",\"balanceEth\":0,\"network\":\"SEPOLIA\"\\}$" "$KEY_HEADER"
fi

if [ "$FAILURES" -gt 0 ]; then
  echo "$FAILURES verificação(ões) falharam."
  exit 1
fi
echo "Todas as verificações passaram."
